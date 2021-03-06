/**
 *  This class implements the SYN operator for all retrieval models.
 *  The synonym operator creates a new inverted list that is the union
 *  of its constituents.  Typically it is used for morphological or
 *  conceptual variants, e.g., #SYN (cat cats) or #SYN (cat kitty) or
 *  #SYN (astronaut cosmonaut).
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class QryopIlSyn extends QryopIl {

    /**
     * It is convenient for the constructor to accept a variable number
     * of arguments. Thus new QryopIlSyn (arg1, arg2, arg3, ...).
     */
    public QryopIlSyn(Qryop... q) {
        for (int i = 0; i < q.length; i++)
            this.args.add(q[i]);
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

        //  Each pass of the loop adds 1 document to result until all of
        //  the inverted lists are depleted.  When a list is depleted, it
        //  is removed from argPtrs, so this loop runs until argPtrs is empty.

        //  This implementation is intended to be clear.  A more efficient
        //  implementation would combine loops and use merge-sort.

        while (this.argPtrs.size() > 0) {

            int nextDocid = getSmallestCurrentDocid();

            //  Create a new posting that is the union of the posting lists
            //  that match the nextDocid.

            List<Integer> positions = new ArrayList<Integer>();

            for (int i = 0; i < this.argPtrs.size(); i++) {
                ArgPtr ptri = this.argPtrs.get(i);

                if (ptri.invList.getDocid(ptri.nextDoc) == nextDocid) {
                    positions.addAll(ptri.invList.postings.get(ptri.nextDoc).positions);
                    ptri.nextDoc++;
                }
            }

            Collections.sort(positions);
            result.invertedList.appendPosting(nextDocid, positions);

            //  If an ArgPtr has reached the end of its list, remove it.
            //  The loop is backwards so that removing an arg does not
            //  interfere with iteration.

            for (int i = this.argPtrs.size() - 1; i >= 0; i--) {
                ArgPtr ptri = this.argPtrs.get(i);

                if (ptri.nextDoc >= ptri.invList.postings.size()) {
                    this.argPtrs.remove(i);
                }
            }
        }

        freeArgPtrs();

        return result;
    }

    /**
     * Return the smallest unexamined docid from the ArgPtrs.
     *
     * @return The smallest internal document id.
     */
    public int getSmallestCurrentDocid() {

        int nextDocid = Integer.MAX_VALUE;

        for (int i = 0; i < this.argPtrs.size(); i++) {
            ArgPtr ptri = this.argPtrs.get(i);
            int currDocId = ptri.invList.postings.size() > 0 ? ptri.invList.getDocid(ptri.nextDoc) : Integer.MAX_VALUE;
            nextDocid = Math.min(currDocId, nextDocid);
        }

        return (nextDocid);
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

        return ("#SYN( " + result + ")");
    }
}
