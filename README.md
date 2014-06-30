MiTextExplorer
==============

Website: **[brenocon.com/te](http://brenocon.com/te)**

<img width=500 src="http://brenocon.com/te/hella.png">

The **Mutual information Text Explorer** is a tool that allows interactive exploration of text data and document covariates.
See [the paper](http://brenocon.com/oconnor.mitextexplorer.illvi2014.pdf) 
or [slides](http://brenocon.com/oconnor.mitextexplorer.slides.illvi2014.pdf)
for information.
Currently, an experimental system is available. It is very buggy, use with caution, etc etc. Contact brenocon@gmail.com ([http://brenocon.com](http://brenocon.com)) with questions.  

How to run
==========

Get the application: <b><a href=http://brenocon.com/te/te.jar>te.jar</a></b>

Get one of the example datasets: <a href=http://brenocon.com/te/bible.zip>bible.zip</a> or <a href=http://brenocon.com/te/sotu.zip>sotu.zip</a>.

Launch it with one argument, the configuration file of the corpus you want to view.  For example:

    java -jar te.jar sotu/config.conf

This requires Java version 8 to be accessible from the commandline.  Check the version with `java -version`; it must be at least `"1.8.0"`.
(Sometimes, you might have to give a flag to specify memory usage, like `java -Xmx2g`. I'm not sure when this is necessary.)

Data format
===========

Each line is one document, encoded as a JSON object.
There is one mandatory key:

 * `text`: a string of the document's text.

There is one optional special key:

 * `id` (or `docid`): a string that is a unique identifier for this document. Not necessary for simple usage.

Other keys in the JSON object are covariates.
They have to be listed in the `schema` configuration to be used. 

TODO: automatic type detection

TODO: covariates in separate file and CSV

Configuration options
=====================

The application is launched by giving it the full path to the main config file.
For an example to adapt to your own data, start with bible/config.conf.

Required configuration parameters include:

  * `data`: the filename of the data. Either absolute, or relative to the config file's directory.
  * `schema`: an object that describes the types of the covariates; see below. Or, a filename for an external schema config.
  * `x`, `y`: which covariates should be the x- and y-axes.

Other configuration parameters include:

  * `tokenizer`: what tokenizer to run. Options are 
    - `StanfordTokenizer`, which is good for traditionally edited text. (Default.)
    - `SimpleTokenizer`, which tokenizes only on whitespace. If you want to run your own tokenizer, an easy way to use it is to encode your tokenization into the `text` field by putting spaces between the tokens, and then use `SimpleTokenizer`. On real text, this tokenizer gives poor results.  But it is fast.
  * `nlp_file`: this is an alternative to `tokenizer`. It says you don't want the application to run any NLP routines, and instead read off all NLP annotations from an external file. It relies on the `id` document identifiers in order to merge the annotations against the text and covariates.  I don't have documentation for the format, but it is produced by [this](https://github.com/brendano/myutil/blob/master/src/corenlp/Parse.java).  Currently this is the only way to get part-of-speech and named entity annotations into the system.

In the `schema` object (or schema config file), every key is the name of a covariate, and the type is given.  Legal types are

 * `number`, for a numeric variable (either integer or floating point is fine).
 * `categorical`, a categorical variable (a.k.a. discrete, or what R calls a factor).
   In the data, the values for a categorical variable are represented as
   strings.  In the schema, you can optionally specify a list of possible
   values.  The ordering you give will be the order it is displayed in.  See
   bible.zip for an example.

TODO: reconcile with / extend to http://dataprotocols.org/json-table-schema/ which seems to be a moderately sensible data column typing system (are there better ones? there are certainly many worse ones.)

The format for the config file is a lax form of JSON, described [here](https://github.com/typesafehub/config/blob/master/HOCON.md).  Any legal JSON can be used for the config file; it has a few niceties like commenting with `#`, being able to sometimes skip quoting, and leaving off commas when using a separate line per entry.

Source code
===========

License is GPL v2 or later.  I'd be happy to do BSD/MIT or something, but the software uses some GPL'd libraries which I find convenient.  

Code is at [github.com/brendano/te](https://github.com/brendano/te).

Dependencies have to be placed in `lib/` for `./build.sh` to work.
For development in an IDE, I just manually add them to the build path.
I've placed a copy of them here: [te-deps.zip](http://brenocon.com/te/te-deps.zip).
The dependencies are currently:

    config-1.2.1.jar
    docking-frames-common.jar
    docking-frames-core.jar
    guava-13.0.1.jar
    jackson-all-1.9.11.jar
    myutil.jar
    stanford-corenlp-3.2.0.jar
    trove-3.0.3.jar
