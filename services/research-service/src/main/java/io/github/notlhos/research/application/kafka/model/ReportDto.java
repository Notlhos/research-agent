package io.github.notlhos.research.application.kafka.model;

import java.util.List;

/**
 * Report written by the writer agent; {@code citedIds} lists every arXiv id cited in the text.
 */
public record ReportDto(String title, String summary, List<ReportSectionDto> sections, List<String> citedIds) {
}
