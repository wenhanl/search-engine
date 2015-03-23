import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by wenhanl on 15-1-31.
 */
public class QryopSlOr extends QryopSl {

    public QryopSlOr(Qryop... q) {
        for (int i = 0; i < q.length; i++)
            this.args.add(q[i]);
    }


    @Override
    public void add(Qryop q) throws IOException {
        this.args.add(q);
    }

    @Override
    public QryResult evaluate(RetrievalModel r) throws IOException {

        return (evaluateBoolean(r));
    }

    public QryResult evaluateBoolean(RetrievalModel r) throws IOException {

        //  Initialization

        allocArgPtrs(r);
        QryResult result = new QryResult();

        // Use a hashmap to record max score
        HashMap<Integer, Double> scoreMap = new HashMap<Integer, Double>();

        // Use a set to remove duplicate
        HashSet<Integer> set = new HashSet<Integer>();
        for (int k = 0; k < this.argPtrs.size(); k++) {
            ArgPtr ptr = this.argPtrs.get(k);

            for (int j = 0; j < ptr.scoreList.scores.size(); j++) {
                int ptrDocid = ptr.scoreList.getDocid(j);
                set.add(ptrDocid);
                if (r instanceof RetrievalModelRankedBoolean) {
                    if (!scoreMap.containsKey(ptrDocid)) {
                        scoreMap.put(ptrDocid, ptr.scoreList.scores.get(j).getScore());
                    } else {
                        double currScore = ptr.scoreList.scores.get(j).getScore();
                        if (currScore > scoreMap.get(ptrDocid)) {
                            scoreMap.put(ptrDocid, currScore);
                        }
                    }
                }
            }
        }

        for (int i : set) {
            if (r instanceof RetrievalModelUnrankedBoolean)
                result.docScores.scores.add(new ScoreListEntry(i, 1.0));
            else
                result.docScores.scores.add(new ScoreListEntry(i, scoreMap.get(i)));
        }

        freeArgPtrs();

        return result;
    }

    @Override
    public String toString() {
        String result = new String();

        for (int i = 0; i < this.args.size(); i++)
            result += this.args.get(i).toString() + " ";

        return ("#OR( " + result + ")");
    }


    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        if (r instanceof RetrievalModelUnrankedBoolean)
            return (0.0);

        return 0.0;
    }
}
