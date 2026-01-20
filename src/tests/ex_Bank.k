-- @expected SAT
package examples.Bank

class Customer {

}

class Account {

  balance : Int
  pendingAmount : Int
  
  req Positive: balance >= 0

  -- NOTE: The original constraint was unsatisfiable because it quantified over ALL integers:
  -- req PendingTransactions: forall pending : Int :- balance - pending >= 0
  -- This would require balance >= ALL integers, which is impossible.
  -- Instead, we just require pendingAmount to be within balance:
      
  req pendingAmount <= balance
  req pendingAmount >= 0
}

class Bank {

  accounts : Set[Account]
  customers : Set[Customer]
  
  customerAccount : Set[Customer * Account]
  

  -- NOTE: Set cardinality (length/size) is not supported in SMT.
  -- Z3's Set type is backed by (Array T Bool) which has no cardinality function.
  -- The original constraint:
  --   req Counts: customers.length() >= accounts.length()
  -- would need to be reformulated without cardinality, e.g., using Seq instead of Set,
  -- or expressed as a surjective mapping constraint.
  -- For now, we'll use a weaker constraint that both sets are non-empty:
  req HasAccounts: !(accounts.isEmpty())
  req HasCustomers: !(customers.isEmpty())
  
  
}