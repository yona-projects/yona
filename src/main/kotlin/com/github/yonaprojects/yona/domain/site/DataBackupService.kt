package com.github.yonaprojects.yona.domain.site

interface DataBackupService {
    /** DB에 존재하는 모든 테이블의 전체 행을 JSON으로 직렬화해 반환한다. */
    fun exportAll(): ByteArray

    /** exportAll()이 만든 백업을 읽어 각 테이블을 통째로 교체(복원)한다. */
    fun importAll(bytes: ByteArray)
}
