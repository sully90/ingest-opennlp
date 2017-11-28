# Elasticsearch opennlp Ingest Processor

Based on the work of Alexaner Reelsen.
Copyright 2016-2017 Alexander Reelsen https://github.com/spinscale/elasticsearch-ingest-opennlp

Note, for Elasticsearch 5.6.4 (this branch), build with:
```
./gradlew clean check
```

## Usage

This is how you configure a pipeline with support for opennlp

You can add the following lines to the `config/elasticsearch.yml` (as those models are shipped by default, they are easy to enable). The models are looked up in the `config/ingest-opennlp/` directory.

```
ingest.opennlp.model.file.persons: en-ner-persons.bin
ingest.opennlp.model.file.dates: en-ner-dates.bin
ingest.opennlp.model.file.locations: en-ner-locations.bin
```

Now fire up Elasticsearch and configure a pipeline

```
PUT _ingest/pipeline/opennlp-pipeline
{
  "description": "A pipeline to do named entity extraction",
  "processors": [
    {
      "opennlp" : {
        "field" : "my_field"
      }
    }
  ]
}

PUT /my-index/my-type/1?pipeline=opennlp-pipeline
{
  "my_field" : "Kobe Bryant was one of the best basketball players of all times. Not even Michael Jordan has ever scored 81 points in one game. Munich is really an awesome city, but New York is as well. Yesterday has been the hottest day of the year."
}

GET /my-index/my-type/1
{
  "my_field" : "Kobe Bryant was one of the best basketball players of all times. Not even Michael Jordan has ever scored 81 points in one game. Munich is really an awesome city, but New York is as well. Yesterday has been the hottest day of the year.",
  "entities" : {
    "locations" : [ "Munich", "New York" ],
    "dates" : [ "Yesterday" ],
    "names" : [ "Kobe Bryant", "Michael Jordan" ]
  }
}
```

You can also specify only certain named entities in the processor, i.e. if you only want to extract names


```
PUT _ingest/pipeline/opennlp-pipeline
{
  "description": "A pipeline to do named entity extraction",
  "processors": [
    {
      "opennlp" : {
        "field" : "my_field"
        "fields" : [ "names" ]
      }
    }
  ]
}
```

## Configuration

You can configure own models per field, the setting for this is prefixed `ingest.opennlp.model.file.`. So you can configure any model with any field name, by specifying a name and a path to file, like the three examples below:

| Parameter | Use |
| --- | --- |
| ingest.opennlp.model.file.name     | Configure the file for named entity recognition for the field name    |
| ingest.opennlp.model.file.date     | Configure the file for date entity recognition for the field date     |
| ingest.opennlp.model.file.person   | Configure the file for person entity recognition for the field date     |
| ingest.opennlp.model.file.WHATEVER | Configure the file for WHATEVER entity recognition for the field date     |

## Setup

In order to install this plugin, you need to create a zip distribution first by running

```bash
gradle clean check
```

This will produce a zip file in `build/distributions`. As part of the build, the models are packaged into the zip file, but need to be downloaded before. There is a special task in the `build.gradle` which is downloading the models, in case they dont exist.

After building the zip file, you can install it like this

```bash
bin/plugin install file:///path/to/elasticsearch-ingest-opennlp/build/distribution/ingest-opennlp-0.0.1-SNAPSHOT.zip
```

There is no need to configure anything, as the models art part of the zip file.

## Bugs & TODO

* A couple of groovy build mechanisms from core are disabled. See the `build.gradle` for further explanations
* Only the most basic NLP functions are exposed, please fork and add your own code to this!
