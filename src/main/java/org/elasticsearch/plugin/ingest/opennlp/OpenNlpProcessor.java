/*
 * Copyright [2016] [Alexander Reelsen]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.elasticsearch.plugin.ingest.opennlp;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.ingest.ConfigurationUtils.readList;
import static org.elasticsearch.ingest.ConfigurationUtils.readOptionalList;
import static org.elasticsearch.ingest.ConfigurationUtils.readStringProperty;

public class OpenNlpProcessor extends AbstractProcessor {

    public static final String TYPE = "opennlp";

    private final OpenNlpService openNlpService;
    private final List<String> sourceFields;
    private final String targetField;
    private final Set<String> fields;

    OpenNlpProcessor(OpenNlpService openNlpService, String tag, List<String> sourceFields, String targetField, Set<String> fields) throws
            IOException {
        super(tag);
        this.openNlpService = openNlpService;
        this.sourceFields = sourceFields;
        this.targetField = targetField;
        this.fields = fields;
    }

    @Override
    public void execute(IngestDocument ingestDocument) throws Exception {
        for (String sourceField : this.sourceFields) {
            String content = ingestDocument.getFieldValue(sourceField, String.class);

            if (Strings.hasLength(content)) {
                Map<String, Set<String>> entities = new HashMap<>();
                mergeExisting(entities, ingestDocument, targetField);

                for (String field : fields) {
                    Set<String> data = openNlpService.find(content, field);
                    merge(entities, field, data);
                }

                ingestDocument.setFieldValue(targetField, entities);

                if (this.openNlpService.sentimentModelEnabled()) {
                    // Sentiment
                    String sentiment = openNlpService.getSentiment(content);
                    ingestDocument.setFieldValue("opennlp.sentiment", sentiment);
                }
            }
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory implements Processor.Factory {

        private final Logger logger;

        private OpenNlpService openNlpService;

        public Factory(OpenNlpService openNlpService) {
            this.openNlpService = openNlpService;
            this.logger = Loggers.getLogger(getClass(), openNlpService.getSettings());
        }

        @Override
        public OpenNlpProcessor create(Map<String, Processor.Factory> registry, String processorTag, Map<String, Object> config)
                throws Exception {
//            String field = readStringProperty(TYPE, processorTag, config, "field");
            logger.info("processorTag: {}", processorTag);
            logger.info("config: {}", config);
            List<String> documentFields = readList(TYPE, processorTag, config, "field");
            logger.info("documentFields: {}", documentFields);
            String targetField = readStringProperty(TYPE, processorTag, config, "target_field", "entities");
            List<String> fields = readOptionalList(TYPE, processorTag, config, "fields");
            final Set<String> foundFields = fields == null || fields.size() == 0 ? openNlpService.getModels() : new HashSet<>(fields);
            return new OpenNlpProcessor(openNlpService, processorTag, documentFields, targetField, foundFields);
        }
    }

    private static void mergeExisting(Map<String, Set<String>> entities, IngestDocument ingestDocument, String targetField) {
        if (ingestDocument.hasField(targetField)) {
            @SuppressWarnings("unchecked")
            Map<String, Set<String>> existing = ingestDocument.getFieldValue(targetField, Map.class);
            entities.putAll(existing);
        } else {
            ingestDocument.setFieldValue(targetField, entities);
        }
    }

    private static void merge(Map<String, Set<String>> map, String key, Set<String> values) {
        if (values.size() == 0) return;

        if (map.containsKey(key))
            values.addAll(map.get(key));

        map.put(key, values);
    }
}
