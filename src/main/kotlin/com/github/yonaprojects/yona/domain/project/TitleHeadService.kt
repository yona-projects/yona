package com.github.yonaprojects.yona.domain.project

// yona TitleHead.saveTitleHeadKeyword()/deleteTitleHeadKeyword()/findByProject() 대응.
interface TitleHeadService {
    fun saveTitleHeadKeyword(project: Project, title: String)
    fun deleteTitleHeadKeyword(project: Project, title: String)
    fun search(project: Project, query: String): List<TitleHead>
}
