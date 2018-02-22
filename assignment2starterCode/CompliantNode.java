import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {

    private final double p_graph;
    private final double p_malicous;
    private final double p_txDistribution;
    private final int numRounds;
    private boolean[] followees = new boolean[0];
    private int numFollowees = 0;
    private Set<Transaction> pendingTransactions = new HashSet<Transaction>();
    private Map<Transaction, Set<Integer>> proposedTransactions = new HashMap<Transaction, Set<Integer>>();

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        // IMPLEMENT THIS
        this.p_graph = p_graph;
        this.p_malicous = p_malicious;
        this.p_txDistribution = p_txDistribution;
        this.numRounds = numRounds;
    }

    public void setFollowees(boolean[] followees) {
        // IMPLEMENT THIS
        if (followees != null) {
            this.followees = followees.clone();

            for (boolean f : followees) {
                if (f) {
                    numFollowees++;
                }
            }
        }
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        // IMPLEMENT THIS
        if (pendingTransactions != null) {
            this.pendingTransactions = new HashSet<Transaction>(pendingTransactions);

            // clear proposed transactions
            this.proposedTransactions.clear();
        }
    }

    public Set<Transaction> sendToFollowers() {
        // IMPLEMENT THIS
        Set<Transaction> proposals = new HashSet<Transaction>(this.pendingTransactions);
        for (Transaction proposedTransaction : this.proposedTransactions.keySet()) {
            Set<Integer> nodeIds = this.proposedTransactions.get(proposedTransaction);
            if (nodeIds.size() >= 1) {
                proposals.add(proposedTransaction);
            }
        }

        return proposals;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
        // IMPLEMENT THIS
        if (candidates != null) {
            for (Candidate candidate: candidates) {
                if (this.proposedTransactions.containsKey(candidate.tx)) {
                    Set<Integer> nodeIds = this.proposedTransactions.get(candidate.tx);
                    nodeIds.add(candidate.sender);
                } else {
                    Set<Integer> nodeIds = new HashSet<Integer>();
                    nodeIds.add(candidate.sender);
                    this.proposedTransactions.put(candidate.tx, nodeIds);
                }
            }
        }
    }
}
