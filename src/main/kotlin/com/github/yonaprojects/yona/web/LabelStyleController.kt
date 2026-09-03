package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class LabelStyleController(
    private val projectRepository: ProjectRepository,
    private val issueLabelRepository: IssueLabelRepository
) {

    @GetMapping("/{user}/{projectName}/issue/labels.css")
    fun labelStyles(
        @PathVariable user: String,
        @PathVariable projectName: String,
        @RequestHeader(value = "If-None-Match", required = false) ifNoneMatch: String?
    ): ResponseEntity<String> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(user, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val labels = issueLabelRepository.findByProject(project)
        val eTag = "\"${labels.map { "${it.id}-${it.color}" }.hashCode()}\""

        if (ifNoneMatch != null && ifNoneMatch == eTag) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .header("ETag", eTag)
                .build()
        }

        val sb = StringBuilder()
        for (label in labels) {
            val color = label.color
            val id = label.id
            sb.append(".issue-label[data-label-id=\"$id\"]{\n")
            sb.append("    box-shadow: inset 2px 0 0px $color;\n")
            sb.append("    -webkit-box-shadow: inset 2px 0 0px $color;\n")
            sb.append("    -moz-box-shadow: inset 2px 0 0px $color;\n")
            sb.append("}\n")
            sb.append(".issue-label.active[data-label-id=\"$id\"]{\n")
            sb.append("    background-color: $color;\n")
            sb.append("    color: ${getLabelTextColorFromBgColor(color)};\n")
            sb.append("}\n")
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/css;charset=UTF-8"))
            .header("ETag", eTag)
            .body(sb.toString())
    }

    private fun getLabelTextColorFromBgColor(bgColor: String): String {
        val defaultRGB = mapOf("R" to 255, "G" to 255, "B" to 255)
        val lowerBgColor = bgColor.lowercase()

        val rgb = when {
            lowerBgColor.startsWith("rgb") -> {
                try {
                    val start = lowerBgColor.indexOf('(') + 1
                    val end = lowerBgColor.indexOf(')')
                    val parts = lowerBgColor.substring(start, end).split(",")
                    mapOf(
                        "R" to parts[0].trim().toInt(),
                        "G" to parts[1].trim().toInt(),
                        "B" to parts[2].trim().toInt()
                    )
                } catch (e: Exception) {
                    defaultRGB
                }
            }
            lowerBgColor.matches(Regex("^[#]*[0-9a-f]+$")) -> {
                val paramColor = if (!lowerBgColor.startsWith("#")) "#$lowerBgColor" else lowerBgColor
                val baseColor = if (paramColor.length == 4) {
                    "#" + paramColor[1].toString().repeat(2) +
                          paramColor[2].toString().repeat(2) +
                          paramColor[3].toString().repeat(2)
                } else {
                    paramColor
                }
                if (baseColor.length == 7) {
                    try {
                        mapOf(
                            "R" to baseColor.substring(1, 3).toInt(16),
                            "G" to baseColor.substring(3, 5).toInt(16),
                            "B" to baseColor.substring(5, 7).toInt(16)
                        )
                    } catch (e: Exception) {
                        defaultRGB
                    }
                } else {
                    defaultRGB
                }
            }
            else -> defaultRGB
        }

        val r = rgb["R"] ?: 255
        val g = rgb["G"] ?: 255
        val b = rgb["B"] ?: 255
        val colorSpace = (r * 0.21) + (g * 0.72) + (b * 0.07)

        return if (colorSpace > 192) "dimgray" else "white"
    }
}
