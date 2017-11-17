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

import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.ingest.Processor;
import org.elasticsearch.plugins.IngestPlugin;
import org.elasticsearch.plugins.Plugin;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.common.settings.Setting.groupSetting;

public class IngestOpenNlpPlugin extends Plugin implements IngestPlugin {

    public static final Setting<Settings> MODEL_FILE_SETTINGS = groupSetting("ingest.opennlp.model.file.", Setting.Property.NodeScope);
    public static final Setting<Settings> SENTENCE_FILE_SETTINGS =
            groupSetting("ingest.opennlp.tokenizer.file.", Setting.Property.NodeScope);

    @Override
    public List<Setting<?>> getSettings() {
        return Arrays.asList(MODEL_FILE_SETTINGS, SENTENCE_FILE_SETTINGS);
    }

    @Override
    public Map<String, Processor.Factory> getProcessors(Processor.Parameters parameters) {
        Path configDirectory = parameters.env.configFile().resolve("ingest-opennlp");
        OpenNlpService openNlpService = new OpenNlpService(configDirectory, parameters.env.settings());
        openNlpService.start();

        return Collections.singletonMap(OpenNlpProcessor.TYPE, new OpenNlpProcessor.Factory(openNlpService));
    }
}