/**
 * Created by wenhanl on 15-2-16.
 */
class RetrievalModelBM25 extends RetrievalModel {
    private double k_1;
    private double b;
    private double k_3;

    @Override
    public boolean setParameter(String parameterName, double value) {
        if (parameterName.equals("k_1")) {
            this.k_1 = value;
            return true;
        } else if (parameterName.equals("b")) {
            this.b = value;
            return true;
        } else if (parameterName.equals("k_3")) {
            this.k_3 = value;
            return true;
        }
        return false;
    }

    @Override
    public boolean setParameter(String parameterName, String value) {
        if (parameterName.equals("k_1")) {
            this.k_1 = Double.parseDouble(value);
            return true;
        } else if (parameterName.equals("b")) {
            this.b = Double.parseDouble(value);
            return true;
        } else if (parameterName.equals("k_3")) {
            this.k_3 = Double.parseDouble(value);
            return true;
        }
        return false;
    }

    public double getK1() {
        return this.k_1;
    }

    public double getB () {
        return this.b;
    }

    public double getK3() {
        return this.k_3;
    }
}
