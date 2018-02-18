import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.security.PublicKey;

public class TxHandler {

    /**
     * The UTXOPool
     */
    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        if (utxoPool == null) {
            this.utxoPool = new UTXOPool();
        } else {
            this.utxoPool = new UTXOPool(utxoPool);
        }
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        Set<UTXO> utxosClaimedByTx = new HashSet<UTXO>(); // set of claimed UTXOs
        double inputSum = 0; // sum of input values
        double outputSum = 0; // sum of output values

        // for each input
        List<Transaction.Input> inputs = tx.getInputs();
        for (int i = 0; i < inputs.size(); i++) {
            Transaction.Input input = inputs.get(i);

            // check output is in the current UTXO pool
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if (!utxoPool.contains(utxo)) {
                // output not in current UTXO pool
                return false;
            }

            // check UTXO not already claimed
            if (utxosClaimedByTx.contains(utxo)) {
                // utxo already claimed
                return false;
            }
            utxosClaimedByTx.add(utxo);

            // check signature is valid
            Transaction.Output output = utxoPool.getTxOutput(utxo);
            PublicKey pubKey = output.address;
            byte[] rawDataToSign = tx.getRawDataToSign(i);
            if (!Crypto.verifySignature(pubKey, rawDataToSign, input.signature)) {
                // signature not valid
                return false;
            }

            // add sum of input values
            inputSum += output.value;
        }

        // for each output
        List<Transaction.Output> outputs = tx.getOutputs();
        for (Transaction.Output output : outputs) {
            // check value non-negative
            if (output.value < 0) {
                // value is negative
                return false;
            }

            // add sum of output values
            outputSum += output.value;
        }

        // check sum of input values is greater than or equal to sum of output values
        if (inputSum < outputSum) {
            // output greater than input
            return false;
        }

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {

        // get valid transactions and their input UTXOs
        Map<Transaction, Set<UTXO>> validTxsMap = new HashMap<Transaction, Set<UTXO>>(possibleTxs.length);
        for (Transaction tx : possibleTxs) {
            if (isValidTx(tx)) {
                Set<UTXO> utxosClaimedByTx = new HashSet<UTXO>();
                List<Transaction.Input> inputs = tx.getInputs();
                for (Transaction.Input input : inputs) {
                    UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                    utxosClaimedByTx.add(utxo);
                }
                validTxsMap.put(tx, utxosClaimedByTx);
            }
        }

        // get conflicting transactions
        Map<Transaction, Set<Transaction>> conflictingTxsMap = new HashMap<Transaction, Set<Transaction>>(validTxsMap.size());
        Set<Transaction> validTxs1 = validTxsMap.keySet();
        for (Transaction validTx1 : validTxs1) {
            Set<Transaction> conflictingTxs = new HashSet<Transaction>();

            Set<UTXO> utxosClaimedByTx1 = validTxsMap.get(validTx1);
            Set<Transaction> validTxs2 = validTxsMap.keySet();
            for (Transaction validTx2 : validTxs2) {
                if (validTx2.equals(validTx1)) {
                    continue;
                }

                Set<UTXO> utxosClaimedByTx2 = validTxsMap.get(validTx2);
                boolean conflicts = false;
                for (UTXO utxoClaimedByTx2 : utxosClaimedByTx2) {
                    if (utxosClaimedByTx1.contains(utxoClaimedByTx2)) {
                        conflicts = true;
                        break;
                    }
                }
                if (conflicts) {
                    conflictingTxs.add(validTx2);
                }
            }
            if (conflictingTxs.size() > 0) {
                conflictingTxsMap.put(validTx1, conflictingTxs);
            }
        }

        // eliminate conflicting transactions, sorted by greater number of conflicts
        while (!conflictingTxsMap.isEmpty()) {
            Set<Map.Entry<Transaction, Set<Transaction>>> entries = conflictingTxsMap.entrySet();
            List<Map.Entry<Transaction, Set<Transaction>>> sortedEntries = new LinkedList<Map.Entry<Transaction, Set<Transaction>>>(entries);
            Collections.sort(sortedEntries, new Comparator<Map.Entry<Transaction, Set<Transaction>>>() {
                public int compare(Map.Entry<Transaction, Set<Transaction>> o1, Map.Entry<Transaction, Set<Transaction>> o2) {
                    int s1 = o1.getValue().size();
                    int s2 = o2.getValue().size();
                    if (s1 == s2) {
                        return 0;
                    }
                    if (s1 < s2) {
                        return 1;
                    }
                    return -1;
                }
            });

            // eliminate tx that has most conflicts
            Map.Entry<Transaction, Set<Transaction>> entryToEliminate = sortedEntries.get(0);
            conflictingTxsMap.remove(entryToEliminate.getKey());
            validTxsMap.remove(entryToEliminate.getKey());

            // remove conflicting transaction from other conflicting txs
            for (Transaction otherTx : entryToEliminate.getValue()) {
                Set<Transaction> conflictingTxsOfOtherTx = conflictingTxsMap.get(otherTx);
                conflictingTxsOfOtherTx.remove(entryToEliminate.getKey());
                if (conflictingTxsOfOtherTx.isEmpty()) {
                    conflictingTxsMap.remove(otherTx);
                }
            }
        }

        List<Transaction> validTxs = new ArrayList<Transaction>(validTxsMap.keySet());
        for (Transaction validTx : validTxs) {
            // remove utxos claimed by inputs to valid transactions
            List<Transaction.Input> inputs = validTx.getInputs();
            for (Transaction.Input input : inputs) {
                UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                utxoPool.removeUTXO(utxo);
            }

            // add utxos claimed by output of valid transactions
            List<Transaction.Output> outputs = validTx.getOutputs();
            for (int i = 0; i < outputs.size(); i++) {
                Transaction.Output output = outputs.get(i);

                UTXO utxo = new UTXO(validTx.getHash(), i);
                utxoPool.addUTXO(utxo, output);
            }
        }

        return validTxs.toArray(new Transaction[0]);
    }
}
