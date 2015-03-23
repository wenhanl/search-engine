import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

/**
 * Created by wenhanl on 15-2-1.
 */
public class QryopIlWindow extends QryopIl {
    private int window;

    public QryopIlWindow(int w, Qryop... q) {
        this.window = w;
        for (int i = 0; i < q.length; i++)
            this.args.add(q[i]);
    }

    public QryopIlWindow(int n) {
        this.window = n;
    }

    /**
     * Appends an argument to the list of query operator arguments.  This
     * simplifies the design of some query parsing architectures.
     *
     * @param {q} q The query argument (query operator) to append.
     * @return void
     * @throws java.io.IOException
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
     * @throws java.io.IOException
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
                ArrayList<Vector<Integer>> posList = new ArrayList<Vector<Integer>>();

                for (int i = 0; i < argPtrs.size(); i++) {
                    posList.add(argPtrs.get(i).invList.postings.get(pointers[i]).positions);
                }



                while (true) {

                    boolean locEnd = false;
                    int smallestPos = Integer.MAX_VALUE, smallestId = 0, maxPos = -1;
                    // Find list with smallest location
                    for (int i = 0; i < argPtrs.size(); i++) {
                        if (locs[i] >= posList.get(i).size()) {
                            locEnd = true;
                            break;
                        }
                        int pos = posList.get(i).get(locs[i]);
                        if (pos < smallestPos) {
                            smallestPos = pos;
                            smallestId = i;
                        }
                        if (pos > maxPos) {
                            maxPos = pos;
                        }
                    }
                    if (locEnd) {
                        break;
                    }

                    if (maxPos - smallestPos + 1 <= window) {
                        locations.add(maxPos);
                        for (int i = 0; i < posList.size(); i++) {
                            locs[i]++;
                        }
                    } else {
                        locs[smallestId]++;
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
     * Get smallest docid in all argPtrs on index nextDocid
     * @return
     */
    private int getSmallestDocid() {
        int docid = Integer.MAX_VALUE;

        for (int i = 0; i < argPtrs.size(); i++) {
            ArgPtr ptri = argPtrs.get(i);
            if (ptri.nextDoc >= ptri.scoreList.scores.size()) {
                continue;
            }
            docid = Math.min(docid, ptri.scoreList.getDocid(ptri.nextDoc));
        }
        return docid;
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

        return ("#WINDOW/" + window + "( " + result + ")");
    }
}
