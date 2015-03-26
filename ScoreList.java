/**
 *  This class implements the document score list data structure
 *  and provides methods for accessing and manipulating them.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */


import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class ScoreList{

    //  A little utilty class to create a <docid, score> object.

    List<ScoreListEntry> scores = new ArrayList<ScoreListEntry>();

    public void prioritySort() {
        PriorityQueue<ScoreListEntry> queue = new PriorityQueue<ScoreListEntry>(100);

        for (ScoreListEntry entry : scores) {
            try {
                entry.externalId = QryEval.getExternalDocid(entry.getDocid());
            } catch (IOException e) {
                e.printStackTrace();
            }
            queue.offer(entry);
        }

        scores = new ArrayList<ScoreListEntry>();
        while (!queue.isEmpty()) {
            scores.add(queue.poll());
        }
    }

    /**
     * Append a document score to a score list.
     *
     * @param docid An internal document id.
     * @param score The document's score.
     * @return void
     */
    public void add(int docid, double score) {
        scores.add(new ScoreListEntry(docid, score));
    }

    /**
     * Get the n'th document id.
     *
     * @param n The index of the requested document.
     * @return The internal document id.
     */
    public int getDocid(int n) {
        if (this.scores.size() > n) {
            return this.scores.get(n).getDocid();
        }
        return -1;
    }

    /**
     * Get the score of the n'th document.
     *
     * @param n The index of the requested document score.
     * @return The document's score.
     */
    public double getDocidScore(int n) {
        return this.scores.get(n).getScore();
    }


    public boolean isEmpty(){
        return scores.isEmpty();
    }


}
