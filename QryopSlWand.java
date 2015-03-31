import java.io.IOException;

/**
 * Created by wenhanl on 15-2-17.
 */
public class QryopSlWand extends QryopSl{


    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        if (!(r instanceof RetrievalModelIndri)) {
            return (0.0);
        }


        double score = 1;
        for (int i = 0; i < args.size(); i++) {
            score *= Math.pow(((QryopSl) args.get(i)).getDefaultScore(r, docid), args.get(i).weight);
        }

        return score;
    }

    @Override
    public void add(Qryop q) throws IOException {
        args.add(q);
    }

    @Override
    public QryResult evaluate(RetrievalModel r) throws IOException {
        if (!(r instanceof RetrievalModelIndri)) {
            return null;
        }

        allocArgPtrs(r);
        QryResult result = new QryResult();

        if (args.size() == 1) {
            result.docScores = this.argPtrs.get(0).scoreList;
            return result;
        }

        double totalWeight = 0;
        for (int i = 0; i < argPtrs.size(); i++) {
            totalWeight += args.get(i).weight;
        }

        int index = 0;
        while (index < argPtrs.size()) {
            // Get smallest docid in ptri[nextDocid]
            int docid = getSmallestDocid();
            double score = 1.0;

            for (int i = 0; i < argPtrs.size(); i++) {
                ArgPtr ptri = argPtrs.get(i);
                double weight = args.get(i).weight / totalWeight;

                if (ptri.scoreList.scores.size() <= ptri.nextDoc || ptri.scoreList.getDocid(ptri.nextDoc) != docid) {
                    // In case of not equal, mult by defaultScore
                    double tmp = ((QryopSl) args.get(i)).getDefaultScore(r, docid);
                    score *= Math.pow(tmp, weight);
                } else {
                    // In case of equal
                    double tmp = ptri.scoreList.getDocidScore(ptri.nextDoc);
                    score *= Math.pow(tmp, weight);
                    ptri.nextDoc++;

                    // If one ptri has traversed out score list, we know we finished some part
                    if (ptri.nextDoc >= ptri.scoreList.scores.size()) {
                        index++;
                    }
                }
            }

            result.docScores.add(docid, score);

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

    @Override
    public String toString() {
        String result = new String();

        for (int i = 0; i < this.args.size(); i++)
            result += this.args.get(i).toString() + " ";

        return ("#WAND( " + result + ")");
    }
}
