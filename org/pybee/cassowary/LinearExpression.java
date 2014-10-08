// $Id: LinearExpression.java,v 1.31 1999/04/20 00:26:32 gjb Exp $
//
// Cassowary Incremental Constraint Solver
// Original Smalltalk Implementation by Alan Borning
// This Java Implementation by Greg J. Badros, <gjb@cs.washington.edu>
// http://www.cs.washington.edu/homes/gjb
// (C) 1998, 1999 Greg J. Badros and Alan Borning
// See ../LICENSE for legal details regarding this software
//
// LinearExpression
//

package org.pybee.cassowary;

import java.util.*;

public class LinearExpression extends CL
{

  public LinearExpression(AbstractVariable clv, double value, double constant)
    {
      if (CL.fGC)
	System.err.println("new LinearExpression");

      _constant = new ClDouble(constant);
      _terms = new Hashtable(1);
      if (clv != null)
	_terms.put(clv,new ClDouble(value));
    }

  public LinearExpression(double num)
    {
      this(null,0,num);
    }

  public LinearExpression()
    {
      this(0);
    }

  public LinearExpression(AbstractVariable clv, double value)
    {
      this(clv,value,0.0);
    }

  public LinearExpression(AbstractVariable clv)
    {
      this(clv,1,0);
    }


  // for use by the clone method
  protected LinearExpression(ClDouble constant, Hashtable terms)
    {
      if (CL.fGC)
	System.err.println("clone LinearExpression");
      _constant = (ClDouble) constant.clone();
      _terms = new Hashtable();
      // need to unalias the ClDouble-s that we clone (do a deep clone)
      for (Enumeration e = terms.keys(); e.hasMoreElements() ; ) {
	AbstractVariable clv = (AbstractVariable) e.nextElement();
	_terms.put(clv,((ClDouble) terms.get(clv)).clone());
      }
    }


  public LinearExpression multiplyMe(double x)
    {
      _constant.setValue(_constant.doubleValue() * x);

      for (Enumeration e = _terms.keys() ; e.hasMoreElements(); ) {
        AbstractVariable clv = (AbstractVariable) e.nextElement();
	ClDouble cld = (ClDouble) _terms.get(clv);
	cld.setValue(cld.doubleValue() * x);
      }
      return  this;
    }

  public final Object clone()
    {
      return new LinearExpression(_constant,_terms);
    }

  public final LinearExpression times(double x)
    {
      return ((LinearExpression) clone()).multiplyMe(x);
    }

  public final LinearExpression times(LinearExpression expr)
       throws NonlinearExpression
    {
      if (isConstant())
	{
	return expr.times(_constant.doubleValue());
	}
      else if (!expr.isConstant())
	{
	throw new NonlinearExpression();
	}
      return times(expr._constant.doubleValue());
    }

  public final LinearExpression plus(LinearExpression expr)
    {
      return ((LinearExpression) clone()).addExpression(expr,1.0);
    }

  public final LinearExpression plus(Variable var)
    throws NonlinearExpression
    {
      return ((LinearExpression) clone()).addVariable(var,1.0);
    }

  public final LinearExpression minus(LinearExpression expr)
    {
      return ((LinearExpression) clone()).addExpression(expr,-1.0);
    }

  public final LinearExpression minus(Variable var)
       throws NonlinearExpression
    {
      return ((LinearExpression) clone()).addVariable(var,-1.0);
    }


  public final LinearExpression divide(double x)
    throws NonlinearExpression
    {
      if (CL.approx(x,0.0))
	{
	throw new NonlinearExpression();
	}
      return times(1.0/x);
    }

  public final LinearExpression divide(LinearExpression expr)
    throws NonlinearExpression
    {
      if (!expr.isConstant())
	{
	throw new NonlinearExpression();
	}
      return divide(expr._constant.doubleValue());
    }

  public final LinearExpression divFrom(LinearExpression expr)
    throws NonlinearExpression
    {
      if (!isConstant() || CL.approx(_constant.doubleValue(),0.0))
	{
	throw new NonlinearExpression();
	}
      return expr.divide(_constant.doubleValue());
    }

  public final LinearExpression subtractFrom(LinearExpression expr)
    { return expr.minus( this); }

  // Add n*expr to this expression from another expression expr.
  // Notify the solver if a variable is added or deleted from this
  // expression.
  public final LinearExpression addExpression(LinearExpression expr, double n,
						AbstractVariable subject,
						Tableau solver)
    {
      incrementConstant(n * expr.constant());

      for (Enumeration e = expr.terms().keys() ; e.hasMoreElements(); ) {
	AbstractVariable clv = (AbstractVariable) e.nextElement();
 	double coeff = ((ClDouble) expr.terms().get(clv)).doubleValue();
 	addVariable(clv,coeff*n, subject, solver);
      }
      return this;
    }

  // Add n*expr to this expression from another expression expr.
  public final LinearExpression addExpression(LinearExpression expr, double n)
    {
      incrementConstant(n * expr.constant());

      for (Enumeration e = expr.terms().keys() ; e.hasMoreElements(); ) {
        AbstractVariable clv = (AbstractVariable) e.nextElement();
	double coeff = ((ClDouble) expr.terms().get(clv)).doubleValue();
	addVariable(clv,coeff*n);
      }
      return this;
    }

  public final LinearExpression addExpression(LinearExpression expr)
    {
      return addExpression(expr,1.0);
    }

  // Add a term c*v to this expression.  If the expression already
  // contains a term involving v, add c to the existing coefficient.
  // If the new coefficient is approximately 0, delete v.
  public final LinearExpression addVariable(AbstractVariable v, double c)
    { // body largely duplicated below
      if (fTraceOn) fnenterprint("addVariable:" + v + ", " + c);

      ClDouble coeff = (ClDouble) _terms.get(v);
      if (coeff != null) {
	double new_coefficient = coeff.doubleValue() + c;
	if (CL.approx(new_coefficient,0.0)) {
	  _terms.remove(v);
	}
	else {
	  coeff.setValue(new_coefficient);
	}
      } else {
	if (!CL.approx(c,0.0)) {
	  _terms.put(v,new ClDouble(c));
	}
      }
      return this;
    }

  public final LinearExpression addVariable(AbstractVariable v)
  { return addVariable(v,1.0); }


  public final LinearExpression setVariable(AbstractVariable v, double c)
    {
      //assert(c != 0.0);
      ClDouble coeff = (ClDouble) _terms.get(v);
      if (coeff != null)
	coeff.setValue(c);
      else
	_terms.put(v,new ClDouble(c));
      return this;
    }

  // Add a term c*v to this expression.  If the expression already
  // contains a term involving v, add c to the existing coefficient.
  // If the new coefficient is approximately 0, delete v.  Notify the
  // solver if v appears or disappears from this expression.
  public final LinearExpression addVariable(AbstractVariable v, double c,
 					AbstractVariable subject, Tableau solver)
     { // body largely duplicated above
       if (fTraceOn) fnenterprint("addVariable:" + v + ", " + c + ", " + subject + ", ...");

       ClDouble coeff = (ClDouble) _terms.get(v);
       if (coeff != null) {
	 double new_coefficient = coeff.doubleValue() + c;
	 if (CL.approx(new_coefficient,0.0)) {
	   solver.noteRemovedVariable(v,subject);
	   _terms.remove(v);
	 } else {
	   coeff.setValue(new_coefficient);
	 }
       } else {
	 if (!CL.approx(c,0.0)) {
	   _terms.put(v,new ClDouble(c));
	   solver.noteAddedVariable(v,subject);
	 }
       }
       return this;
     }

  // Return a pivotable variable in this expression.  (It is an error
  // if this expression is constant -- signal InternalError in
  // that case).  Return null if no pivotable variables
  public final AbstractVariable anyPivotableVariable() throws InternalError
    {
      if (isConstant())
	{
	throw new InternalError("anyPivotableVariable called on a constant");
	}

      for (Enumeration e = _terms.keys() ; e.hasMoreElements(); ) {
        AbstractVariable clv = (AbstractVariable) e.nextElement();
        if (clv.isPivotable())
          return clv;
      }

      // No pivotable variables, so just return null, and let the caller
      // error if needed
      return null;
    }

  // Replace var with a symbolic expression expr that is equal to it.
  // If a variable has been added to this expression that wasn't there
  // before, or if a variable has been dropped from this expression
  // because it now has a coefficient of 0, inform the solver.
  // PRECONDITIONS:
  //   var occurs with a non-zero coefficient in this expression.
  public final void substituteOut(AbstractVariable var, LinearExpression expr,
 			    AbstractVariable subject, Tableau solver)
  {
    if (fTraceOn) fnenterprint("CLE:substituteOut: " + var + ", " + expr + ", " + subject + ", ...");
    if (fTraceOn) traceprint("this = " + this);

    double multiplier = ((ClDouble) _terms.remove(var)).doubleValue();
    incrementConstant(multiplier * expr.constant());

    for (Enumeration e = expr.terms().keys(); e.hasMoreElements(); ) {
      AbstractVariable clv = (AbstractVariable) e.nextElement();
      double coeff = ((ClDouble) expr.terms().get(clv)).doubleValue();
      ClDouble d_old_coeff = (ClDouble) _terms.get(clv);
      if (d_old_coeff != null) {
	double old_coeff = d_old_coeff.doubleValue();
	double newCoeff = old_coeff + multiplier * coeff;
	if (CL.approx(newCoeff,0.0)) {
	  solver.noteRemovedVariable(clv,subject);
	  _terms.remove(clv);
	} else {
	  d_old_coeff.setValue(newCoeff);
	}
      } else {
	// did not have that variable already
	_terms.put(clv,new ClDouble(multiplier * coeff));
	solver.noteAddedVariable(clv,subject);
      }
    }
    if (fTraceOn) traceprint("Now this is " + this);
  }

  // This linear expression currently represents the equation
  // oldSubject=self.  Destructively modify it so that it represents
  // the equation newSubject=self.
  //
  // Precondition: newSubject currently has a nonzero coefficient in
  // this expression.
  //
  // NOTES
  //   Suppose this expression is c + a*newSubject + a1*v1 + ... + an*vn.
  //
  //   Then the current equation is
  //       oldSubject = c + a*newSubject + a1*v1 + ... + an*vn.
  //   The new equation will be
  //        newSubject = -c/a + oldSubject/a - (a1/a)*v1 - ... - (an/a)*vn.
  //   Note that the term involving newSubject has been dropped.
  public final void changeSubject(AbstractVariable old_subject, AbstractVariable new_subject)
    {
      ClDouble cld = (ClDouble) _terms.get(old_subject);
      if (cld != null)
	cld.setValue(newSubject(new_subject));
      else
	_terms.put(old_subject,new ClDouble(newSubject(new_subject)));
    }

  // This linear expression currently represents the equation self=0.  Destructively modify it so
  // that subject=self represents an equivalent equation.
  //
  // Precondition: subject must be one of the variables in this expression.
  // NOTES
  //   Suppose this expression is
  //     c + a*subject + a1*v1 + ... + an*vn
  //   representing
  //     c + a*subject + a1*v1 + ... + an*vn = 0
  // The modified expression will be
  //    subject = -c/a - (a1/a)*v1 - ... - (an/a)*vn
  //   representing
  //    subject = -c/a - (a1/a)*v1 - ... - (an/a)*vn
  //
  // Note that the term involving subject has been dropped.
  // Returns the reciprocal, so changeSubject can use it, too
  public final double newSubject(AbstractVariable subject)
    {
      if (fTraceOn) fnenterprint("newSubject:" + subject);
      ClDouble coeff = (ClDouble) _terms.remove(subject);
      double reciprocal = 1.0 / coeff.doubleValue();
      multiplyMe(-reciprocal);
      return reciprocal;
    }

  // Return the coefficient corresponding to variable var, i.e.,
  // the 'ci' corresponding to the 'vi' that var is:
  //     v1*c1 + v2*c2 + .. + vn*cn + c
  public final double coefficientFor(AbstractVariable var)
    {
      ClDouble coeff = (ClDouble) _terms.get(var);
      if (coeff != null)
	return coeff.doubleValue();
      else
	return 0.0;
    }

  public final double constant()
    { return _constant.doubleValue(); }

  public final void set_constant(double c)
    {
      _constant.setValue(c);
    }

  public final Hashtable terms()
    { return _terms; }

  public final void incrementConstant(double c)
    {
      _constant.setValue(_constant.doubleValue() + c);
    }

  public final boolean isConstant()
    { return _terms.size() == 0; }

  public final String toString()
    {
      StringBuffer bstr = new StringBuffer();
      Enumeration e = _terms.keys();

      if (!CL.approx(_constant.doubleValue(),0.0) || _terms.size() == 0)
	{
	bstr.append(_constant.toString());
	}
      else
	{
	if (_terms.size() == 0)
	  {
	  return bstr.toString();
	  }
	AbstractVariable clv = (AbstractVariable) e.nextElement();
	ClDouble coeff = (ClDouble) _terms.get(clv);
	bstr.append(coeff.toString() + "*" + clv.toString());
	}
      for (; e.hasMoreElements(); )
	{
	AbstractVariable clv = (AbstractVariable) e.nextElement();
	ClDouble coeff = (ClDouble) _terms.get(clv);
	bstr.append(" + " + coeff.toString() + "*" + clv.toString());
	}
      return bstr.toString();
    }

  public final static LinearExpression Plus(LinearExpression e1, LinearExpression e2)
    { return e1.plus(e2); }

  public final static LinearExpression Minus(LinearExpression e1, LinearExpression e2)
    { return e1.minus(e2); }

  public final static LinearExpression Times(LinearExpression e1, LinearExpression e2)
    throws NonlinearExpression
    { return e1.times(e2); }

  public final static LinearExpression Divide(LinearExpression e1, LinearExpression e2)
    throws NonlinearExpression
    { return e1.divide(e2); }

  public final static boolean FEquals(LinearExpression e1, LinearExpression e2)
    { return e1 == e2; }

  private ClDouble _constant;
  private Hashtable _terms; // from Variable to ClDouble

}
