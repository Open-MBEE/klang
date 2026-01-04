import com.microsoft.z3.*;

public class TestZ3 {
    public static void main(String[] args) {
        try {
            System.out.println("Testing Z3 library loading...");
            Context ctx = new Context();
            System.out.println("Z3 Context created successfully!");
            BoolExpr b = ctx.mkBoolConst("b");
            Solver s = ctx.mkSolver();
            s.add(b);
            Status status = s.check();
            System.out.println("Z3 solver status: " + status);
            ctx.close();
            System.out.println("Z3 test completed successfully!");
        } catch (Exception e) {
            System.out.println("Z3 test failed with exception:");
            e.printStackTrace();
        }
    }
}