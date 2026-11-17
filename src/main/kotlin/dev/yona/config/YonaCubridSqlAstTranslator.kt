package dev.yona.config

import org.hibernate.community.dialect.CUBRIDSqlAstTranslator
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.query.sqm.function.SelfRenderingFunctionSqlAstExpression
import org.hibernate.sql.ast.SqlAstTranslator
import org.hibernate.sql.ast.spi.SqlAppender
import org.hibernate.sql.ast.tree.SqlAstNode
import org.hibernate.sql.ast.tree.Statement
import org.hibernate.sql.ast.tree.expression.Expression
import org.hibernate.sql.ast.tree.expression.SelfRenderingExpression
import org.hibernate.sql.ast.tree.predicate.LikePredicate
import org.hibernate.sql.exec.spi.JdbcOperation

/**
 * Cubrid용 SQL 번역기이다(`decisions.md` A-630). 기본 [CUBRIDSqlAstTranslator]에 하나를 보탠다: `lower(x) like 패턴`을 `x collate utf8_en_ci like 패턴`으로 그린다.
 *
 * - 일치하는 모양은 `like`의 왼쪽이 인자 하나짜리 `lower(...)`일 때이다. 그 안이 문자열로 바꾸는 `cast(x as string)`이면 그것도 벗긴다(Cubrid의 긴 문자열 열은 이미 `string`이라 변환이 필요 없다).
 * - 그 밖의 `like`(왼쪽이 `lower`가 아닌 것)와 [CubridCaseInsensitiveCollation.enabled]가 꺼져 있을 때는 기본 번역을 그대로 쓴다.
 * - 패턴 쪽은 건드리지 않는다. 정렬 규칙이 대소문자를 무시하므로 `lower(패턴)`이어도 결과가 같다.
 */
class YonaCubridSqlAstTranslator<T : JdbcOperation>(sessionFactory: SessionFactoryImplementor, statement: Statement) : CUBRIDSqlAstTranslator<T>(sessionFactory, statement) {

    /**
     * `like`의 왼쪽 식은 [renderLikePredicate]가 아니라 이 메서드가 그린다(`renderLikePredicate`는 패턴과 이스케이프만 그린다). 그래서 이 메서드에서 왼쪽 식을 바꾼 `like`로 갈아 끼운다.
     */
    override fun visitLikePredicate(likePredicate: LikePredicate) {
        val operand = if (CubridCaseInsensitiveCollation.enabled && likePredicate.isCaseSensitive) caseInsensitiveOperand(likePredicate.matchExpression) else null
        if (operand == null) {
            super.visitLikePredicate(likePredicate)
            return
        }
        super.visitLikePredicate(
            LikePredicate(
                Collated(operand, likePredicate.matchExpression), likePredicate.pattern, likePredicate.escapeCharacter,
                likePredicate.isNegated, likePredicate.isCaseSensitive, likePredicate.expressionType
            )
        )
    }

    /**
     * `lower(x)`나 `lower(cast(x as string))`이면 `x`를 돌려주고, 아니면 `null`이다. 인자는 [Expression]이 아닐 수 있어(열 참조를 감싼 경로 해석 객체) [SqlAstNode]로 다룬다.
     */
    private fun caseInsensitiveOperand(expression: Expression): SqlAstNode? {
        val lower = expression as? SelfRenderingFunctionSqlAstExpression<*> ?: return null
        if (!lower.functionName.equals("lower", ignoreCase = true) || lower.arguments.size != 1) return null
        val argument: SqlAstNode = lower.arguments[0]
        val cast = argument as? SelfRenderingFunctionSqlAstExpression<*>
        if (cast != null && cast.functionName.equals("cast", ignoreCase = true) && cast.arguments.size == 2) return cast.arguments[0]
        return argument
    }

    /** `x collate utf8_en_ci`로 그리는 식이다. 형식은 원래 `like`의 왼쪽 식과 같다. */
    private class Collated(private val operand: SqlAstNode, private val original: Expression) : SelfRenderingExpression {
        override fun getExpressionType() = original.expressionType

        override fun renderToSql(sqlAppender: SqlAppender, walker: SqlAstTranslator<*>, sessionFactory: SessionFactoryImplementor) {
            operand.accept(walker)
            sqlAppender.appendSql(" collate ")
            sqlAppender.appendSql(CubridCaseInsensitiveCollation.NAME)
        }
    }
}
