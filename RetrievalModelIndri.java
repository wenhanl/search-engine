/**
 * Created by wenhanl on 15-2-16.
 */
class RetrievalModelIndri extends RetrievalModel {
    private int mu;
    private double lambda;

    @Override
    public boolean setParameter(String parameterName, double value) {
        return false;
    }

    @Override
    public boolean setParameter(String parameterName, String value) {
        if (parameterName.equals("mu")) {
            this.mu = Integer.parseInt(value);
            return true;
        } else if (parameterName.equals("lambda")) {
            this.lambda = Double.parseDouble(value);
            return true;
        }
        return false;
    }

    public int getMu() {
        return this.mu;
    }

    public double getLambda() {
        return this.lambda;
    }
}
