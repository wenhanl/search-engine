/**
 *  This class implements the SCORE operator for all retrieval models.
 *  The single argument to a score operator is a query operator that
 *  produces an inverted list.  The SCORE operator uses this
 *  information to produce a score list that contains document ids and
 *  scores.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.Iterator;

public class QryopSlScore extends QryopSl {

    private double lambda;
    private double mu;
    private double indriIDF;
    private String field;

    /**
     * Construct a new SCORE operator.  The SCORE operator accepts just
     * one argument.
     *
     * @param q The query operator argument.
     * @return @link{QryopSlScore}
     */
    public QryopSlScore(Qryop q) {
        this.args.add(q);
    }

    public QryopSlScore(Qryop q, double weight) {
        this.weight = weight;
        this.args.add(q);
    }

    /**
     * Construct a new SCORE operator.  Allow a SCORE operator to be
     * created with no arguments.  This simplifies the design of some
     * query parsing architectures.
     *
     * @return @link{QryopSlScore}
     */
    public QryopSlScore() {
    }

    /**
     * Appends an argument to the list of query operator arguments.  This
     * simplifies the design of some query parsing architectures.
     *
     * @param a The query argument to append.
     */
    public void add(Qryop a) {
        this.args.add(a);
    }

    /**
     * Evaluate the query operator.
     *
     * @param r A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws IOException
     */
    public QryResult evaluate(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelUnrankedBoolean || r instanceof RetrievalModelRankedBoolean) {
            return (evaluateBoolean(r));
        } else if (r instanceof RetrievalModelBM25) {
            return (evaluateBM((RetrievalModelBM25) r));
        } else if (r instanceof RetrievalModelIndri) {
            // Load params at once to avoid repeat
            loadParams(r);
            return (evaluateIndri((RetrievalModelIndri) r));
        }
        return null;
    }

    /**
     * Evaluate the query operator for boolean retrieval models.
     *
     * @param r A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws IOException
     */
    public QryResult evaluateBoolean(RetrievalModel r) throws IOException {

        // Evaluate the query argument.

        QryResult result = args.get(0).evaluate(r);

        // Each pass of the loop computes a score for one document. Note:
        // If the evaluate operation above returned a score list (which is
        // very possible), this loop gets skipped.

        for (int i = 0; i < result.invertedList.df; i++) {

            // DIFFERENT RETRIEVAL MODELS IMPLEMENT THIS DIFFERENTLY.
            // Unranked Boolean. All matching documents get a score of 1.0.
            if (r instanceof RetrievalModelUnrankedBoolean) {
                result.docScores.add(result.invertedList.postings.get(i).docid,
                        (float) 1.0);
            } else if (r instanceof RetrievalModelRankedBoolean) {
                result.docScores.add(result.invertedList.postings.get(i).docid,
                        (double) result.invertedList.postings.get(i).tf);
            }
        }

        // The SCORE operator should not return a populated inverted list.
        // If there is one, replace it with an empty inverted list.

        if (result.invertedList.df > 0)
            result.invertedList = new InvList();

        return result;
    }

    public QryResult evaluateBM(RetrievalModelBM25 r) throws  IOException{
        QryResult result = args.get(0).evaluate(r);

        // Get global params
        double k1 = r.getK1(), b = r.getB();
        int df = result.invertedList.df;
        int numDocs = QryEval.READER.numDocs();
        String field = result.invertedList.field;

        // Get doc related params and do calculation
        for (int i = 0; i < df; i++) {
            double idf = Math.log((numDocs - df + 0.5) / (df + 0.5));
            if (idf <= 0) {
                continue;
            }
            int tf = result.invertedList.getTf(i);

            long docLen = QryEval.dls.getDocLength(field, result.invertedList.getDocid(i));
            double avgLen = QryEval.READER.getSumTotalTermFreq(field) / (double) QryEval.READER.getDocCount(field);

            double tfWeight = tf / (tf + k1 * ((1-b) + b * docLen/avgLen));
            double score = idf * tfWeight;
            result.docScores.add(result.invertedList.getDocid(i), score);
        }

        if (result.invertedList.df > 0)
            result.invertedList = new InvList();
        return result;
    }

    public QryResult evaluateIndri(RetrievalModelIndri r) throws IOException {
        QryResult result = args.get(0).evaluate(r);

        for (int i = 0; i < result.invertedList.df; i++) {
            InvList.DocPosting currDoc = result.invertedList.postings.get(i);
            double indriScore = (1 - lambda) * (currDoc.tf + mu * indriIDF) / (QryEval.dls.getDocLength(field, currDoc.docid) + mu) + lambda * indriIDF;
            result.docScores.add(currDoc.docid, indriScore);
        }


        if (result.invertedList.df > 0)
            result.invertedList = new InvList();

        return result;
    }

    /**
     * Load necessary params of Indri at once to avoid repeatedly call
     * @param r
     * @throws IOException
     */
    private void loadParams(RetrievalModel r) throws IOException{
        RetrievalModelIndri ir = (RetrievalModelIndri) r;
        lambda = ir.getLambda();
        mu = ir.getMu();
        InvList currInv = args.get(0).evaluate(r).invertedList;
        indriIDF = (double) currInv.ctf / QryEval.READER.getSumTotalTermFreq(currInv.field);
        field = currInv.field;
    }
    /*
     *  Calculate the default score for a document that does not match
     *  the query argument.  This score is 0 for many retrieval models,
     *  but not all retrieval models.
     *  @param r A retrieval model that controls how the operator behaves.
     *  @param docid The internal id of the document that needs a default score.
     *  @return The default score.
     */
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {

        if (!(r instanceof RetrievalModelIndri))
            return (0.0);

        double docLength = QryEval.dls.getDocLength(field, (int) docid);

        return (1.0 - lambda) * mu * indriIDF / (docLength + mu) + lambda * indriIDF;
    }

    /**
     * Return a string version of this query operator.
     *
     * @return The string version of this query operator.
     */
    public String toString() {

        String result = new String();

        for (Iterator<Qryop> i = this.args.iterator(); i.hasNext(); )
            result += (i.next().toString() + " ");

        return ("#SCORE( " + result + ")");
    }
}
