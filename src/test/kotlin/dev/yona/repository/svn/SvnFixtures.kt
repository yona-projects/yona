package dev.yona.repository.svn

import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.core.wc.SVNWCUtil
import java.io.File
import java.nio.file.Files

/** Subversion 테스트에서 저장소에 커밋을 만드는 도구이다. 임시 작업 복사본으로 체크아웃, 추가, 커밋한다. */
object SvnFixtures {

    init {
        FSRepositoryFactory.setup()
    }

    /**
     * [directory]의 저장소에 파일을 추가하거나 고치고 지우는 커밋 하나를 만든다. 없는 상위 폴더는 만든다.
     *
     * @param author 커밋 작성자(`svn:author`)
     * @param files 경로(앞의 `/` 없이)와 내용. 이미 있는 경로는 고친다
     * @param deletes 지울 경로
     * @return 새 리비전 번호
     */
    fun commit(directory: File, author: String, message: String, files: Map<String, ByteArray> = emptyMap(), deletes: List<String> = emptyList()): Long {
        val manager = SVNClientManager.newInstance(SVNWCUtil.createDefaultOptions(true), BasicAuthenticationManager.newInstance(author, charArrayOf()))
        val workingCopy = Files.createTempDirectory("svnwc").toFile()
        try {
            manager.updateClient.doCheckout(SVNURL.fromFile(directory), workingCopy, SVNRevision.HEAD, SVNRevision.HEAD, SVNDepth.INFINITY, false)
            for ((path, content) in files) {
                val file = File(workingCopy, path)
                val existed = file.exists()
                file.parentFile.mkdirs()
                file.writeBytes(content)
                if (!existed) manager.wcClient.doAdd(file, true, false, false, SVNDepth.INFINITY, false, true)
            }
            for (path in deletes) manager.wcClient.doDelete(File(workingCopy, path), true, false)
            return manager.commitClient.doCommit(arrayOf(workingCopy), false, message, null, null, false, false, SVNDepth.INFINITY).newRevision
        } finally {
            manager.dispose()
            workingCopy.deleteRecursively()
        }
    }
}
