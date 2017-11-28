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

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentiment.SentimentME;
import opennlp.tools.sentiment.SentimentModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.util.Supplier;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.StopWatch;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.set.Sets;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenNLP name finders are not thread safe, so we load them via a thread local hack
 */
public class OpenNlpService {

    private final Path configDirectory;
    private final Logger logger;
    private Settings settings;

    private ThreadLocal<TokenNameFinderModel> nerThreadLocal = new ThreadLocal<>();
    private ThreadLocal<SentimentModel> sentimentThreadLocal = new ThreadLocal<>();

    private Map<String, TokenNameFinderModel> nameFinderModels = new ConcurrentHashMap<>();
    private SentimentModel sentimentModel;

    public OpenNlpService(Path configDirectory, Settings settings) {
        this.logger = Loggers.getLogger(getClass(), settings);
        this.configDirectory = configDirectory;
        this.settings = settings;
    }

    public Set<String> getModels() {
        return IngestOpenNlpPlugin.MODEL_FILE_SETTINGS.get(settings).getAsMap().keySet();
    }

    protected OpenNlpService start() {
        StopWatch sw = new StopWatch("models-loading");
        Map<String, String> settingsMap = IngestOpenNlpPlugin.MODEL_FILE_SETTINGS.get(settings).getAsMap();
        for (Map.Entry<String, String> entry : settingsMap.entrySet()) {
            String name = entry.getKey();
            sw.start(name);
            Path path = configDirectory.resolve(entry.getValue());
            try (InputStream is = Files.newInputStream(path)) {
                nameFinderModels.put(name, new TokenNameFinderModel(is));
            } catch (IOException e) {
                logger.error((Supplier<?>) () -> new ParameterizedMessage("Could not load model [{}] with path [{}]", name, path), e);
            }
            sw.stop();
        }

        if (settingsMap.keySet().size() == 0) {
            logger.error("Did not load any models for ingest-opennlp plugin, none configured");
        } else {
            logger.info("Read models in [{}] for {}", sw.totalTime(), settingsMap.keySet());
        }

        if (this.sentimentModelEnabled()) {
            this.sentimentModel = loadSentimentModel();
        }

        return this;
    }

    public Set<String> find(String content, String field) {
        try {
            if (!nameFinderModels.containsKey(field)) {
                throw new ElasticsearchException("Could not find field [{}], possible values {}", field, nameFinderModels.keySet());
            }
            TokenNameFinderModel finderModel = nameFinderModels.get(field);
            if (nerThreadLocal.get() == null || !nerThreadLocal.get().equals(finderModel)) {
                nerThreadLocal.set(finderModel);
            }

            //Instantiating the SentenceDetectorME class
            SentenceModel sentenceModel = loadSentenceModel();
            SentenceDetectorME detector = new SentenceDetectorME(sentenceModel);

            //Detecting the sentence
            String sentences[] = detector.sentDetect(content);

            Set<String> set = new HashSet<>();

            for (String sentence : sentences) {

                String[] tokens = SimpleTokenizer.INSTANCE.tokenize(sentence);
                Span spans[] = new NameFinderME(finderModel).find(tokens);
                String[] names = Span.spansToStrings(spans, tokens);

                set.addAll(Sets.newHashSet(names));
            }
//            return Sets.newHashSet(names);
            return set;
        } finally {
            nerThreadLocal.remove();
        }
    }

    public String getSentiment(String content) {
        if (!this.sentimentModelEnabled()) {
            throw new RuntimeException("Sentiment model not enabled.");
        }
        try{
            SentimentModel sentimentModel = this.sentimentModel;
            if (sentimentThreadLocal.get() == null || !sentimentThreadLocal.get().equals(sentimentModel)) {
                sentimentThreadLocal.set(sentimentModel);
            }
            SentimentME sentimentME = new SentimentME(sentimentModel);
            String sentiment = toSimpleSentiment(sentimentME.predict(content));
            return sentiment;
        } finally {
            sentimentThreadLocal.remove();
        }
    }

    private static String toSimpleSentiment(String rawSentiment) {
        switch (rawSentiment.toLowerCase(Locale.UK)) {
            case "angry":
                return "Very negative";
            case "sad":
                return "Negative";
            case "neutral":
                return "Neutral";
            case "like":
                return "Positive";
            case "love":
                return "Very positive";
            default:
                return "";
        }
    }

    public boolean sentimentModelEnabled() {
        return Setting.groupSetting("ingest.opennlp.misc.file.").exists(settings);
    }

    private SentimentModel loadSentimentModel() {

        if (this.sentimentModelEnabled()) {

            String name = "sentiment";
            String modelName = Setting.groupSetting("ingest.opennlp.misc.file.").get(settings).get(name);

            Path path = configDirectory.resolve(modelName);

            try {
                SentimentModel sentimentModel = new SentimentModel(path.toUri().toURL());
                return sentimentModel;
            } catch (IOException e) {
                logger.error((Supplier<?>) () -> new ParameterizedMessage("Could not load sentiment model"), e);
            }
        }

        return null;
    }

    private SentenceModel loadSentenceModel() {
        String name = "sentences";
        String modelName = Setting.groupSetting("ingest.opennlp.tokenizer.file.").get(settings).get(name);
        Path path = configDirectory.resolve(modelName);

        try (InputStream is = Files.newInputStream(path)) {
            SentenceModel sentenceModel = new SentenceModel(is);
            return sentenceModel;
        } catch (IOException e) {
            logger.error((Supplier<?>) () -> new ParameterizedMessage("Could not load model [{}] with path [{}]", name, path), e);
        }

        return null;
    }

    public Settings getSettings() {
        return settings;
    }
}
