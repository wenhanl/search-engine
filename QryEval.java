/**
 *  QryEval illustrates the architecture for the portion of a search
 *  engine that evaluates queries.  It is a template for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.*;
import java.util.*;

class QueryRes{
    String id;
    QryResult result;

    QueryRes(String id, QryResult res) {
        this.id = id;
        this.result = res;
    }
}

public class QryEval {

    public static IndexReader READER;
    public static DocLengthStore dls;

    //  The index file reader is accessible via a global variable. This
    //  isn't great programming style, but the alternative is for every
    //  query operator to store or pass this value, which creates its
    //  own headaches.
    public static EnglishAnalyzerConfigurable analyzer =
            new EnglishAnalyzerConfigurable(Version.LUCENE_43);

    //  Create and configure an English analyzer that will be used for
    //  query parsing.
    static {
        analyzer.setLowercase(true);
        analyzer.setStopwordRemoval(true);
        analyzer.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
    }
    static String usage = "Usage:  java " + System.getProperty("sun.java.command")
            + " paramFile\n\n";

    /**
     * @param args The only argument is the path to the parameter file.
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

//        long maxBytes = Runtime.getRuntime().maxMemory();
//        System.out.println("Max memory: " + maxBytes / 1024 / 1024 + "M");

        // must supply parameter file
        if (args.length < 1) {
            System.err.println(usage);
            System.exit(1);
        }

        // read in the parameter file; one parameter per line in format of key=value
        Map<String, String> params = new HashMap<String, String>();
        Scanner scan = new Scanner(new File(args[0]));
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split("=");
            params.put(pair[0].trim(), pair[1].trim());
        } while (scan.hasNext());
        scan.close();

        // parameters required for this example to run
        if (!params.containsKey("indexPath")) {
            System.err.println("Error: Parameters were missing.");
            System.exit(1);
        }

        // open the index
        READER = DirectoryReader.open(FSDirectory.open(new File(params.get("indexPath"))));

        if (READER == null) {
            System.err.println(usage);
            System.exit(1);
        }

        RetrievalModel model = null;

        String modelName = params.get("retrievalAlgorithm");
        if (modelName.equals("UnrankedBoolean")) {
            model = new RetrievalModelUnrankedBoolean();
        } else if (modelName.equals("RankedBoolean")){
            model = new RetrievalModelRankedBoolean();
        } else if (modelName.equals("BM25")) {
            model = new RetrievalModelBM25();
            if (!(model.setParameter("k_1", params.get("BM25:k_1")) && model.setParameter("b", params.get("BM25:b"))
                    && model.setParameter("k_3", params.get("BM25:k_3")))) {
                System.err.println("BM25 missing necessary params");
                System.exit(1);
            }
        } else if (modelName.equals("Indri")) {
            model = new RetrievalModelIndri();
            if (!(model.setParameter("mu", params.get("Indri:mu")) && model.setParameter("lambda", params.get("Indri:lambda")))) {
                System.err.println("BM25 missing necessary params");
                System.exit(1);
            }
        } else {
            System.err.println(usage);
            System.exit(1);
        }

        dls = new DocLengthStore(READER);

        long startTime = System.currentTimeMillis();

        BufferedWriter writer = null;

        try {
            writer = new BufferedWriter(new FileWriter(new File(params.get("trecEvalOutputPath"))));
        } catch (Exception e) {
            e.printStackTrace();
        }

        ArrayList<QueryRes> results = new ArrayList<QueryRes>();

        if (!params.containsKey("fb") || !params.get("fb").equals("true") || !params.containsKey("fbInitialRankingFile")) {
            // Old way to retrieve documents
            getResultsFromQuery(params.get("queryFilePath"), results, model);
        } else {
            if (params.containsKey("fbInitialRankingFile")) {
                // Load result from result file
                getResultFromFile(params.get("fbInitialRankingFile"), results);
            } else {
                getResultsFromQuery(params.get("queryFilePath"), results, model);
            }

            // Query expansion begin

        }


        for (QueryRes res : results) {
            writeResults(writer, res.id, res.result);
        }

        try {
            writer.close();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();

        System.out.println("Running time: " + (endTime - startTime) + " ms.");
        printMemoryUsage(false);

    }

    /**
     * Run query retrieval to get results
     * @param paramFileName
     * @param results
     * @param model
     */
    static void getResultsFromQuery(String paramFileName, ArrayList<QueryRes> results, RetrievalModel model) {
        Scanner scan = null;
        try {
            scan = new Scanner(new File(paramFileName));

            do {
                String line = scan.nextLine();
                String[] pair = line.split(":");
                String queryId = pair[0];
                String queryString = pair[1];
                Qryop qTree = parseQuery(queryString, model);
                QryResult result = qTree.evaluate(model);
                result.docScores.prioritySort();
                results.add(new QueryRes(queryId, result));

            } while (scan.hasNext());
            scan.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * If initial result file provided,
     * @param resultFile
     * @param results
     */
    static void getResultFromFile(String resultFile, ArrayList<QueryRes> results) {
        // Load results form init file
        QryResult res = new QryResult();
        String currId = "";
        try {
            Scanner scan = new Scanner(new File(resultFile));
            do {
                String line = scan.nextLine();
                String[] pair = line.split(" ");
                String queryId = pair[0];
                int docid = getInternalDocid(pair[2]);
                double score = Double.valueOf(pair[4]);

                if (!currId.equals(queryId) && !res.docScores.isEmpty()) {
                    results.add(new QueryRes(currId, res));
                    res = new QryResult();
                }

                currId = queryId;
                res.docScores.scores.add(new ScoreListEntry(docid, score));


            } while (scan.hasNext());
            scan.close();

            results.add(new QueryRes(currId, res));
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    /**
     * Write an error message and exit.  This can be done in other
     * ways, but I wanted something that takes just one statement so
     * that it is easy to insert checks without cluttering the code.
     *
     * @param message The error message to write before exiting.
     * @return void
     */
    static void fatalError(String message) {
        System.err.println(message);
        System.exit(1);
    }

    /**
     * Get the external document id for a document specified by an
     * internal document id. If the internal id doesn't exists, returns null.
     *
     * @param iid The internal document id of the document.
     * @throws IOException
     */
    static String getExternalDocid(int iid) throws IOException {
        Document d = QryEval.READER.document(iid);
        String eid = d.get("externalId");
        return eid;
    }

    /**
     * Finds the internal document id for a document specified by its
     * external id, e.g. clueweb09-enwp00-88-09710.  If no such
     * document exists, it throws an exception.
     *
     * @param externalId The external document id of a document.s
     * @return An internal doc id suitable for finding document vectors etc.
     * @throws Exception
     */
    static int getInternalDocid(String externalId) throws Exception {
        Query q = new TermQuery(new Term("externalId", externalId));

        IndexSearcher searcher = new IndexSearcher(QryEval.READER);
        TopScoreDocCollector collector = TopScoreDocCollector.create(1, false);
        searcher.search(q, collector);
        ScoreDoc[] hits = collector.topDocs().scoreDocs;

        if (hits.length < 1) {
            throw new Exception("External id not found.");
        } else {
            return hits[0].doc;
        }
    }

    /**
     * parseQuery converts a query string into a query tree.
     *
     * @param qString A string containing a query.
     * @throws IOException
     */
    static Qryop parseQuery(String qString, RetrievalModel model) throws IOException {

        Qryop currentOp = null;
        Stack<Qryop> stack = new Stack<Qryop>();

        // Add a default query operator to an unstructured query. This
        // is a tiny bit easier if unnecessary whitespace is removed.

        qString = qString.trim();

        //if (qString.charAt(0) != '#') {
        //qString = "#or(" + qString + ")";

        if (model instanceof RetrievalModelUnrankedBoolean || model instanceof RetrievalModelRankedBoolean) {
            qString = "#or(" + qString + ")";
        } else if (model instanceof RetrievalModelBM25) {
            qString = "#sum(" + qString + ")";
        } else if (model instanceof RetrievalModelIndri) {
            qString = "#and(" + qString + ")";
        }


        // Tokenize the query.

        StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
        String token = null;

        double currWeight = -1;

        // Each pass of the loop processes one token. To improve
        // efficiency and clarity, the query operator on the top of the
        // stack is also stored in currentOp.

        while (tokens.hasMoreTokens()) {

            token = tokens.nextToken();

            //System.out.println(token);

            if (token.matches("[ ,(\t\n\r]")) {
                // Ignore most delimiters.
            } else if (token.equalsIgnoreCase("#and")) {

                Qryop newOp = new QryopSlAnd();
                if (currentOp instanceof QryopSlWand || currentOp instanceof QryopSlWsum) {
                    newOp.weight = currWeight;
                    currWeight = -1;
                }
                stack.push(newOp);
                currentOp = newOp;
            } else if (token.equalsIgnoreCase("#syn")) {
                currentOp = new QryopIlSyn();
                stack.push(currentOp);
            } else if (token.equalsIgnoreCase("#or")) {
                currentOp = new QryopSlOr();
                stack.push(currentOp);
            } else if (token.equalsIgnoreCase("#wand")) {
                currentOp = new QryopSlWand();
                stack.push(currentOp);
            } else if (token.equalsIgnoreCase("#wsum")) {
                currentOp = new QryopSlWsum();
                stack.push(currentOp);
            } else if (token.length() > 6 && token.toLowerCase().startsWith("#near/")) {
                int nearN = Integer.valueOf(token.substring(6));
                currentOp = new QryopIlNear(nearN);
                stack.push(currentOp);
            } else if (token.length() > 8 && token.toLowerCase().startsWith("#window/")) {
                int window = Integer.valueOf(token.substring(8));
                currentOp = new QryopIlWindow(window);
                stack.push(currentOp);
            } else if (token.equalsIgnoreCase("#syn")) {
                currentOp = new QryopIlSyn();
                stack.push(currentOp);
            } else if (token.equalsIgnoreCase("#sum")) {
                currentOp = new QryopSlSum();
                stack.push(currentOp);
            } else if (token.startsWith(")")) { // Finish current query operator.
                // If the current query operator is not an argument to
                // another query operator (i.e., the stack is empty when it
                // is removed), we're done (assuming correct syntax - see
                // below). Otherwise, add the current operator as an
                // argument to the higher-level operator, and shift
                // processing back to the higher-level operator.

                stack.pop();

                if (stack.empty())
                    break;

                Qryop arg = currentOp;
                if (arg.args.size() > 0) {
                    currentOp = stack.peek();
                    currentOp.add(arg);
                }
            } else {

                String[] terms = null;
                String[] split = null;
                if (currentOp instanceof QryopSlWand || currentOp instanceof QryopSlWsum) {
                    if (currWeight < 0) {
                        // Weight not set
                        currWeight = Double.valueOf(token);
                    } else {
                        split = token.split("\\.");
                        if (split.length == 2) {
                            terms = tokenizeQuery(split[0]);
                            if (terms.length > 0) {
                                QryopIlTerm toadd = new QryopIlTerm(terms[0], split[1]);
                                toadd.weight = currWeight;
                                currentOp.add(toadd);
                            }
                        } else {
                            terms = tokenizeQuery(token);
                            if (terms.length > 0) {
                                QryopIlTerm toadd = new QryopIlTerm(terms[0]);
                                toadd.weight = currWeight;
                                currentOp.add(toadd);
                            }
                        }
                        // Set weight flag back
                        currWeight = -1;
                    }
                    continue;
                }
                // NOTE: You should do lexical processing of the token before
                // creating the query term, and you should check to see whether
                // the token specifies a particular field (e.g., apple.title).
                terms = null;
                split = token.split("\\.");
                if (split.length == 2) {
                    terms = tokenizeQuery(split[0]);
                    if (terms.length > 0) {
                        currentOp.add(new QryopIlTerm(terms[0], split[1]));
                    }
                } else {
                    terms = tokenizeQuery(token);
                    if (terms.length > 0) {
                        currentOp.add(new QryopIlTerm(terms[0]));
                    }
                }
            }
        }

        // A broken structured query can leave unprocessed tokens on the
        // stack, so check for that.

        if (tokens.hasMoreTokens()) {
            System.err.println("Error:  Query syntax is incorrect.  " + qString);
            return null;
        }

        return currentOp;
    }

    /**
     * Print a message indicating the amount of memory used.  The
     * caller can indicate whether garbage collection should be
     * performed, which slows the program but reduces memory usage.
     *
     * @param gc If true, run the garbage collector before reporting.
     * @return void
     */
    public static void printMemoryUsage(boolean gc) {

        Runtime runtime = Runtime.getRuntime();

        if (gc) {
            runtime.gc();
        }

        System.out.println("Memory used:  " +
                ((runtime.totalMemory() - runtime.freeMemory()) /
                        (1024L * 1024L)) + " MB");
    }

    /**
     * Print the query results.
     * <p/>
     * THIS IS NOT THE CORRECT OUTPUT FORMAT.  YOU MUST CHANGE THIS
     * METHOD SO THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK
     * PAGE, WHICH IS:
     * <p/>
     * QueryID Q0 DocID Rank Score RunID
     *
     * @param queryName Original query.
     * @param result    Result object generated by {@link Qryop# evaluate()}.
     * @throws IOException
     */
    static void printResults(String queryName, QryResult result) throws IOException {

        System.out.println(queryName + ":  ");
        if (result.docScores.scores.size() < 1) {
            System.out.println("\tNo results.");
        } else {
            for (int i = 0; i < result.docScores.scores.size(); i++) {
                System.out.println("\t" + i + ":  "
                        + getExternalDocid(result.docScores.getDocid(i))
                        + ", "
                        + result.docScores.getDocidScore(i));
            }
        }
    }

    static void writeResults(BufferedWriter writer, String queryId, QryResult result) throws IOException{
        if (result.docScores.scores.size() < 1) {
            System.out.println("\tNo results.");
            writer.write(queryId + "\tQ0\tdummy\t1\t0\tfubar\n");
        }
        for (int i = 0; i < result.docScores.scores.size(); i++) {
            if (i == 100) {
                return;
            }
            writer.write(queryId + "\tQ0\t" + getExternalDocid(result.docScores.getDocid(i)) + "\t" + (i+1) + "\t");
            writer.write(result.docScores.getDocidScore(i) + "\t" + "fubar\n");
            writer.flush();
        }
    }

    /**
     * Given a query string, returns the terms one at a time with stopwords
     * removed and the terms stemmed using the Krovetz stemmer.
     * <p/>
     * Use this method to process raw query terms.
     *
     * @param query String containing query
     * @return Array of query tokens
     * @throws IOException
     */
    static String[] tokenizeQuery(String query) throws IOException {

        TokenStreamComponents comp = analyzer.createComponents("dummy", new StringReader(query));
        TokenStream tokenStream = comp.getTokenStream();

        CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
        tokenStream.reset();

        List<String> tokens = new ArrayList<String>();
        while (tokenStream.incrementToken()) {
            String term = charTermAttribute.toString();
            tokens.add(term);
        }
        return tokens.toArray(new String[tokens.size()]);
    }
}
