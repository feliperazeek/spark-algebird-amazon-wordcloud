package common

import scala.collection.JavaConversions._
import scala.concurrent._
import scala.util.Try

import models._

import play.api._
import play.api.Play.current

import opennlp.tools.tokenize._

/**
 * Tokenizer, Stop Words Filter, Stemmer
 * I wanted to use ScalaNLP but unfortunately they don't have chalk availabe for Scala 2.11
 */
package object nlp {

  lazy private[this] val is = Play.resourceAsStream("en-token.bin") match {
    case Some(is) => is
    case _ => sys.error(s"Please make sure en-token.bin from OpenNLP is the app's conf directory (./conf/en-token.bin)!")
  }

  lazy private[this] val model = new TokenizerModel(is)
  lazy private[this] val tokenizer = new TokenizerME(model)

  private[this] val StopWords = Seq("a", "all", "am", "an", "and", "any", "are", "aren't",
    "as", "at", "be", "because", "been", "to", "from", "by",
    "can", "can't", "do", "don't", "didn't", "did", "the", "is", "'", "or", "+", ".", "it", ",", "\"", "!", "in", "for")

  // TODO Use a more user-friendly stemmer so upgrading doesn't become upgrad for example
  lazy private[this] val stemmer = new opennlp.tools.stemmer.PorterStemmer

  implicit class StringNLP(s: String) {

    /**
     * Tokenize -> Filter Stop Words -> Stem
     */
    def words(): Seq[String] = {
      // TODO this can probably be optimized for example reducing the number of passes in the collection
      tokenize
        .filter { w => w.length > 1 }
        .filterNot { w => StopWords contains w.toLowerCase }
        .flatMap { w => stem(w).toOption }
    }

    lazy private[this] val tokenize: Seq[String] = {
      // TODO not sure it's the best idea to just ignore tokenization failures
      Try(tokenizer tokenize s).toOption.map(_.toList) getOrElse Nil
    }

    private def stem(s: String): Try[String] = Try {
      stemmer stem s
    }

  }

}