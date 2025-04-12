package example.projection.to.db;

import java.util.Objects;

public class StakePerResult {
    private double sum;
    private int result;

    public StakePerResult() {
    }

    public double getSum() {
        return sum;
    }

    public void setSum(double sum) {
        this.sum = sum;
    }

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (StakePerResult) obj;
        return Double.doubleToLongBits(this.sum) == Double.doubleToLongBits(that.sum) &&
                this.result == that.result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sum, result);
    }

    @Override
    public String toString() {
        return "StakePerResult[" +
                "sum=" + sum + ", " +
                "result=" + result + ']';
    }

}
