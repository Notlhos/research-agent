package io.github.notlhos.research.core.service;

import io.github.notlhos.research.core.api.web.response.ReportResponse;
import io.github.notlhos.research.core.api.web.response.ResearchRunResponse;
import io.github.notlhos.research.core.api.web.response.SourceResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class ReportRenderer {

    private static final int MAX_AUTHORS = 3;

    String markdown(ResearchRunResponse run) {
        ReportResponse report = run.report();
        if (report == null) {
            return "# " + run.topic() + "\n\nStatus: " + run.status() + "\n";
        }
        StringBuilder out = new StringBuilder()
                .append("# ").append(report.title()).append("\n\n")
                .append(report.summary()).append("\n\n");
        report.sections().forEach(section -> out
                .append("## ").append(section.heading()).append("\n\n")
                .append(section.body()).append("\n\n"));
        out.append("## References\n\n");
        run.sources().forEach(source -> out.append(reference(source)).append('\n'));
        return out.toString();
    }

    /* ***** Private Methods ***** */

    private static String reference(SourceResponse source) {
        return "- [%s] %s. %s (%s). %s".formatted(source.arxivId(), source.title(), authors(source.authors()),
                source.published().substring(0, 4), source.url());
    }

    private static String authors(List<String> names) {
        String listed = names.stream().limit(MAX_AUTHORS).collect(Collectors.joining(", "));
        return names.size() > MAX_AUTHORS ? listed + " et al." : listed;
    }

}
