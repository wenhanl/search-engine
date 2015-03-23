import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by wenhanl on 15-2-17.
 */
public class QryopSlSum extends QryopSl{


    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        return 0;
    }

    @Override
    public void add(Qryop q) throws IOException {
        args.add(q);
    }

    @Override
    public QryResult evaluate(RetrievalModel r) throws IOException {
        if (!(r instanceof RetrievalModelBM25)) {
            return null;
        }

        allocArgPtrs(r);
        QryResult result = new QryResult();

        // Use a hashmap to record sum
        HashMap<Integer, Double> scoreMap = new HashMap<Integer, Double>();

        // Use a set to remove duplicate
        HashSet<Integer> set = new HashSet<Integer>();
        for (int k = 0; k < this.argPtrs.size(); k++) {
            ArgPtr ptr = this.argPtrs.get(k);

            for (int j = 0; j < ptr.scoreList.scores.size(); j++) {
                int ptrDocid = ptr.scoreList.getDocid(j);
                //set.add(ptrDocid);
                if (set.add(ptrDocid)) {
                    scoreMap.put(ptrDocid, ptr.scoreList.getDocidScore(j));
                } else {
                    scoreMap.put(ptrDocid, scoreMap.get(ptrDocid) + ptr.scoreList.getDocidScore(j));
                }
            }
        }

        for (int i : set) {
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

        return ("#SUM( " + result + ")");
    }
}
