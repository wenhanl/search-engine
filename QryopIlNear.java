import java.io.IOException;
import java.util.*;

/**
 * Created by wenhanl on 15-2-1.
 */
public class QryopIlNear extends QryopIl {
    private int nearN;

    public QryopIlNear(int n, Qryop... q) {
        this.nearN = n;
        for (int i = 0; i < q.length; i++)
            this.args.add(q[i]);
    }

    public QryopIlNear(int n) {
        this.nearN = n;
    }

    /**
     * Appends an argument to the list of query operator arguments.  This
     * simplifies the design of some query parsing architectures.
     *
     * @param {q} q The query argument (query operator) to append.
     * @return void
     * @throws IOException
     */
    public void add(Qryop a) {
        this.args.add(a);
    }

    /**
     * Evaluates the query operator, including any child operators and
     * returns the result.
     *
     * @param r A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws IOException
     */
    public QryResult evaluate(RetrievalModel r) throws IOException {

        //  Initialization

        allocArgPtrs(r);
        syntaxCheckArgResults(this.argPtrs);

        QryResult result = new QryResult();
        result.invertedList.field = new String(this.argPtrs.get(0).invList.field);

        int len = argPtrs.size();
        int[] pointers = new int[len];
        while (true) {
            boolean end = false, equal = true;

            // Find the smallest docid accross invlists
            if (pointers[0] >= argPtrs.get(0).invList.postings.size()) {
                break;
            }
            int smallDocId = argPtrs.get(0).invList.getDocid(pointers[0]), smallIndex = 0;
            for (int i = 1; i < len; i++) {
                int curr = argPtrs.get(i).invList.getDocid(pointers[i]);
                if (curr < 0) {
                    end = true;
                    equal = false;
                    break;
                }
                if (smallDocId > curr) {
                    smallDocId = curr;
                    smallIndex = i;
                    equal = false;
                } else if (curr > smallDocId) {
                    equal = false;
                }
            }

            // When a docid found in all inverted list
            if (equal) {
                int[] locs = new int[len];
                ArrayList<Integer> locations = new ArrayList<Integer>();

                // Start from first inverted list location
                for (int i = 0; i < argPtrs.get(0).invList.postings.get(pointers[0]).positions.size(); i++) {

                    int prevLoc = argPtrs.get(0).invList.postings.get(pointers[0]).positions.get(i);
                    boolean match = true;
                    // Test location match using DAAT moving pointers
                    for(int j = 1; j < len; j++) {
                        Vector<Integer> currPos = argPtrs.get(j).invList.postings.get(pointers[j]).positions;
                        if (locs[j] >= currPos.size()) {
                            match = false;
                            break;
                        }
                        int currLoc = currPos.get(locs[j]);

                        while (currLoc < prevLoc) {
                            locs[j]++;
                            if (locs[j] >= currPos.size()) {
                                match = false;
                                break;
                            }
                            currLoc = currPos.get(locs[j]);
                        }

                        if (!match || currLoc - prevLoc > nearN ) {
                            match = false;
                            break;
                        }
                        prevLoc = currLoc;
                    }
                    if (match) {
                       locations.add(prevLoc);
                       for (int k = 1; k < len; k++) {
                           locs[k]++;
                       }
                    }
                }
                if (locations.size() > 0) {
                    result.invertedList.appendPosting(smallDocId, locations);
                }
                // In equal case, move docids in all inverted list forward
                for (int i = 0; i < len; i++) {
                    pointers[i]++;
                }
            } else {
                // In unequal case, move smallest docid
                pointers[smallIndex]++;
            }

            if (end) {
                break;
            }
        }



        freeArgPtrs();

        return result;
    }

    /**
     * syntaxCheckArgResults does syntax checking that can only be done
     * after query arguments are evaluated.
     *
     * @param ptrs A list of ArgPtrs for this query operator.
     * @return True if the syntax is valid, false otherwise.
     */
    public Boolean syntaxCheckArgResults(List<ArgPtr> ptrs) {

        for (int i = 0; i < this.args.size(); i++) {

            if (!(this.args.get(i) instanceof QryopIl))
                QryEval.fatalError("Error:  Invalid argument in " +
                        this.toString());
            else if ((i > 0) &&
                    (!ptrs.get(i).invList.field.equals(ptrs.get(0).invList.field)))
                QryEval.fatalError("Error:  Arguments must be in the same field:  " +
                        this.toString());
        }

        return true;
    }

    /*
     *  Return a string version of this query operator.
     *  @return The string version of this query operator.
     */
    public String toString() {

        String result = new String();

        for (Iterator<Qryop> i = this.args.iterator(); i.hasNext(); )
            result += (i.next().toString() + " ");

        return ("#NEAR/" + nearN + "( " + result + ")");
    }
}
