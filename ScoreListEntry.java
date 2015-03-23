import java.io.IOException;

/**
 * Created by wenhanl on 15-1-31.
 */
public class ScoreListEntry implements Comparable{
    private int docid;
    private double score;
    public String externalId = "";

    public ScoreListEntry(int docid, double score) {
        this.docid = docid;
        this.score = score;
    }

    public int getDocid() {
        return docid;
    }

    public double getScore() {
        return score;
    }

    @Override
    public int compareTo(Object o) {
        ScoreListEntry sl = (ScoreListEntry) o;
        double compareScore = sl.score;
        if (this.score != compareScore) {
            return (compareScore > this.score ? 1 : -1);
        } else {
            if (this.docid == sl.docid) {
                return 0;
            }
            return this.externalId.compareTo(sl.externalId);
        }
        //return this.docid - sl.docid;
    }
}
