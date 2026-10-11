package io.github.notlhos.research.application.kafka;

import java.util.List;

public final class KafkaTopics {

    /* ***** Planning Topics ***** */

    public static final String PLAN_REQUEST = "research_plan_request";

    public static final String PLAN_RESPONSE = "research_plan_response";

    /* ***** Embedding Topics ***** */

    public static final String EMBEDDING_REQUEST = "research_embedding_request";

    public static final String EMBEDDING_RESPONSE = "research_embedding_response";

    /* ***** Report Topics ***** */

    public static final String REPORT_REQUEST = "research_report_request";

    public static final String REPORT_RESPONSE = "research_report_response";

    /* ***** Dead Letters ***** */

    public static final String DEAD_LETTER_SUFFIX = "_dlt";

    public static final List<String> ALL = List.of(
            PLAN_REQUEST, PLAN_RESPONSE, EMBEDDING_REQUEST, EMBEDDING_RESPONSE, REPORT_REQUEST, REPORT_RESPONSE);

    private KafkaTopics() {
    }

    public static String deadLetterOf(String topic) {
        return topic + DEAD_LETTER_SUFFIX;
    }

}
