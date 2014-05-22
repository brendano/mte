MiTextExplorer
==============

Currently, an experimental system is available. Contact brenocon@gmail.com (http://brenocon.com) with questions.  See [the paper](http://brenocon.com/oconnor.mitextexplorer.illvi2014.pdf) for information.

Get the application: <b><a href=te.jar>te.jar</a></b>

Get one of the example datasets: <a href=bible.zip>bible.zip</a> or <a href=sotu.zip>sotu.zip</a>.

Requires Java version 8.  Launch it with one argument, the config file of the corpus you want to view.  For example:

    java -Xmx2g -jar te.jar sotu/config.conf

Data format
===========

Each line is one document, encoded as a JSON object.
There are two mandatory keys:

 * `id` (or `docid`): a string that is a unique identifier for this document.
 * `text`: a string of the document's text.

Other keys in the JSON object are covariates.
They have to be listed in `schema.conf` to be used.

TODO: covariates in separate file? CSV?

Configuration options
=====================

The examples are set up with two config files,

  * `config.conf`: controls the application
  * `schema.conf`: describes the metadata variables.

The application is launched by giving it the full path to the main config file.

See the bible.zip one, it's slightly simpler.

In `config.conf`, parameters include:

  * `data`: the filename of the data. Either absolute, or relative to the config file's directory.
  * `schema`: the schema config file.
  * `x`, `y`: which covariates should be the x- and y-axes.
  * `tokenizer`: what tokenizer to run. Options are `SimpleTokenizer`, which tokenizes only on whitespace, or `StanfordTokenizer`, which is better but slower.
  * `nlp_file`: this is an alternative to `tokenizer`. It says you don't want the application to run any NLP routines, and instead read off all NLP annotations from an external file. It relies on the `id` document identifiers in order to merge the annotations against the text and covariates.  I don't have documentation for the format, but it is produced by [this](https://github.com/brendano/myutil/blob/master/src/corenlp/Parse.java).

In `schema.conf`, every key is the name of a covariate, and the type is given.  Legal types are `numeric` or `categ` (for categorical, what R calls a factor).

For a categorical variable, optionally you can give it a list of possible values.  The ordering you give will be the order it is displayed in.  See bible.zip for an example.

The format for the config files is a lax form of JSON, described [here](https://github.com/typesafehub/config/blob/master/HOCON.md).

Source code
===========

License is GPLv2.  Code will be at [github.com/brendano/te](github.com/brendano/te)
