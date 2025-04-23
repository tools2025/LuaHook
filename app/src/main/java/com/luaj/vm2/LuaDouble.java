/*******************************************************************************
* Copyright (c) 2009-2011 Luaj.com. All rights reserved.
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in
* all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
* THE SOFTWARE.
******************************************************************************/
package com.luaj.vm2;


import com.luaj.vm2.LuaInteger;
import com.luaj.vm2.LuaNumber;
import com.luaj.vm2.LuaString;
import com.luaj.vm2.LuaValue;

/**
 * Extension of {@link LuaNumber} which can hold a Java double as its value.
 * <p>
 * These instance are not instantiated directly by clients, but indirectly 
 * via the static functions {@link com.luaj.vm2.LuaValue#valueOf(int)} or {@link com.luaj.vm2.LuaValue#valueOf(double)}
 * functions.  This ensures that values which can be represented as int 
 * are wrapped in {@link LuaInteger} instead of {@link LuaDouble}.
 * <p>
 * Almost all API's implemented in LuaDouble are defined and documented in {@link com.luaj.vm2.LuaValue}.
 * <p>
 * However the constants {@link #NAN}, {@link #POSINF}, {@link #NEGINF},
 * {@link #JSTR_NAN}, {@link #JSTR_POSINF}, and {@link #JSTR_NEGINF} may be useful 
 * when dealing with Nan or Infinite values. 
 * <p>
 * LuaDouble also defines functions for handling the unique math rules of lua devision and modulo in
 * <ul>
 * <li>{@link #ddiv(double, double)}</li>
 * <li>{@link #ddiv_d(double, double)}</li>
 * <li>{@link #dmod(double, double)}</li>
 * <li>{@link #dmod_d(double, double)}</li>
 * </ul> 
 * <p>
 * @see com.luaj.vm2.LuaValue
 * @see LuaNumber
 * @see LuaInteger
 * @see com.luaj.vm2.LuaValue#valueOf(int)
 * @see com.luaj.vm2.LuaValue#valueOf(double)
 */
public class LuaDouble extends LuaNumber {

	/** Constant LuaDouble representing NaN (not a number) */
	public static final LuaDouble NAN    = new LuaDouble( Double.NaN );
	
	/** Constant LuaDouble representing positive infinity */
	public static final LuaDouble POSINF = new LuaDouble( Double.POSITIVE_INFINITY );
	
	/** Constant LuaDouble representing negative infinity */
	public static final LuaDouble NEGINF = new LuaDouble( Double.NEGATIVE_INFINITY );
	
	/** Constant String representation for NaN (not a number), "nan" */
	public static final String JSTR_NAN    = "nan";
	
	/** Constant String representation for positive infinity, "inf" */
	public static final String JSTR_POSINF = "inf";

	/** Constant String representation for negative infinity, "-inf" */
	public static final String JSTR_NEGINF = "-inf";
	
	/** The value being held by this instance. */
	final double v;

	public static LuaNumber valueOf(double d) {
		//return new LuaDouble(d);
		long id = (long) d;
		return d==id? (LuaNumber) LuaInteger.valueOf(id): (LuaNumber) new LuaDouble(d);
	}
	
	/** Don't allow ints to be boxed by DoubleValues  */
	private LuaDouble(double d) {
		this.v = d;
	}

	public int hashCode() {
		long l = Double.doubleToLongBits(v + 1);
		return ((int)(l>>32)) + (int) l;
	}
	
	public boolean islong() {
		return v == (long) v; 
	}
	public boolean isinttype() {
		return v == (int) v;
	}

	public boolean isint() {
		return v == (int) v;
	}

	public byte    tobyte()        { return (byte) (long) v; }
	public char    tochar()        { return (char) (long) v; }
	public double  todouble()      { return v; }
	public float   tofloat()       { return (float) v; }
	public int     toint()         { return (int) (long) v; }
	public long    tolong()        { return (long) v; }
	public short   toshort()       { return (short) (long) v; }


	public double      optdouble(double defval)        { return v; }
	public int         optint(int defval)              { return (int) (long) v;  }
	public LuaInteger  optinteger(LuaInteger defval)   { return LuaInteger.valueOf((int) (long)v); }
	public long        optlong(long defval)            { return (long) v; }
	
	public LuaInteger  checkinteger()                  { return LuaInteger.valueOf( (int) (long) v ); }
	
	// unary operators
	public com.luaj.vm2.LuaValue neg() { return valueOf(-v); }
	
	// object equality, used for key comparison
	public boolean equals(Object o) { return o instanceof LuaDouble? ((LuaDouble)o).v == v: false; }
	
	// equality w/ metatable processing
	public com.luaj.vm2.LuaValue eq(com.luaj.vm2.LuaValue val )        { return val.raweq(v)? TRUE: FALSE; }
	public boolean eq_b( com.luaj.vm2.LuaValue val )       { return val.raweq(v); }

	// equality w/o metatable processing
	public boolean raweq( com.luaj.vm2.LuaValue val )      { return val.raweq(v); }
	public boolean raweq( double val )        { return v == val; }
	public boolean raweq( long val )           { return v == val; }
	
	// basic binary arithmetic
	public com.luaj.vm2.LuaValue add(com.luaj.vm2.LuaValue rhs )        { return rhs.add(v); }
	public com.luaj.vm2.LuaValue add(double lhs )     { return LuaDouble.valueOf(lhs + v); }
	public com.luaj.vm2.LuaValue sub(com.luaj.vm2.LuaValue rhs )        { return rhs.subFrom(v); }
	public com.luaj.vm2.LuaValue sub(double rhs )        { return LuaDouble.valueOf(v - rhs); }
	public com.luaj.vm2.LuaValue sub(long rhs )        { return LuaDouble.valueOf(v - rhs); }
	public com.luaj.vm2.LuaValue subFrom(double lhs )   { return LuaDouble.valueOf(lhs - v); }
	public com.luaj.vm2.LuaValue mul(com.luaj.vm2.LuaValue rhs )        { return rhs.mul(v); }
	public com.luaj.vm2.LuaValue mul(double lhs )   { return LuaDouble.valueOf(lhs * v); }
	public com.luaj.vm2.LuaValue mul(long lhs )      { return LuaDouble.valueOf(lhs * v); }
	public com.luaj.vm2.LuaValue pow(com.luaj.vm2.LuaValue rhs )        { return rhs.powWith(v); }
	public com.luaj.vm2.LuaValue pow(double rhs )        { return LuaNumber.valueOf(Math.pow(v,rhs)); }
	public com.luaj.vm2.LuaValue pow(long rhs )        { return LuaNumber.valueOf(Math.pow(v,rhs)); }
	public com.luaj.vm2.LuaValue powWith(double lhs )   { return LuaNumber.valueOf(Math.pow(lhs,v)); }
	public com.luaj.vm2.LuaValue powWith(long lhs )      { return LuaNumber.valueOf(Math.pow(lhs,v)); }
	public com.luaj.vm2.LuaValue div(com.luaj.vm2.LuaValue rhs )        { return rhs.divInto(v); }
	public com.luaj.vm2.LuaValue div(double rhs )        { return LuaDouble.ddiv(v,rhs); }
	public com.luaj.vm2.LuaValue div(long rhs )        { return LuaDouble.ddiv(v,rhs); }
	public com.luaj.vm2.LuaValue divInto(double lhs )   { return LuaDouble.ddiv(lhs,v); }
	public com.luaj.vm2.LuaValue mod(com.luaj.vm2.LuaValue rhs )        { return rhs.modFrom(v); }
	public com.luaj.vm2.LuaValue mod(double rhs )        { return LuaDouble.dmod(v,rhs); }
	public com.luaj.vm2.LuaValue mod(long rhs )        { return LuaDouble.dmod(v,rhs); }
	public com.luaj.vm2.LuaValue modFrom(double lhs )   { return LuaDouble.dmod(lhs,v); }


	public com.luaj.vm2.LuaValue idiv(com.luaj.vm2.LuaValue rhs )        { return rhs.idiv((long) v); }
	public com.luaj.vm2.LuaValue idiv(long lhs )     { return LuaInteger.valueOf(lhs / v); }

	public com.luaj.vm2.LuaValue band(com.luaj.vm2.LuaValue rhs )        { return rhs.band((long) v); }
	public com.luaj.vm2.LuaValue band(long lhs )     { return LuaInteger.valueOf(lhs & (long)v); }

	public com.luaj.vm2.LuaValue bor(com.luaj.vm2.LuaValue rhs )        { return rhs.bor((long)v); }
	public com.luaj.vm2.LuaValue bor(long lhs )     { return LuaInteger.valueOf(lhs | (long)v); }

	public com.luaj.vm2.LuaValue bxor(com.luaj.vm2.LuaValue rhs )        { return rhs.bxor((long)v); }
	public com.luaj.vm2.LuaValue bxor(long lhs )     { return LuaInteger.valueOf(lhs ^ (long)v); }

	public com.luaj.vm2.LuaValue shl(com.luaj.vm2.LuaValue rhs )        { return rhs.shl((long)v); }
	public com.luaj.vm2.LuaValue shl(long lhs )     { return LuaInteger.valueOf(lhs << (long)v); }

	public com.luaj.vm2.LuaValue shr(com.luaj.vm2.LuaValue rhs )        { return rhs.shr((long)v); }
	public com.luaj.vm2.LuaValue shr(long lhs )     { return LuaInteger.valueOf(lhs >> (long)v); }

	public com.luaj.vm2.LuaValue bnot()     { return LuaInteger.valueOf( ~(long)v); }


	/** Divide two double numbers according to lua math, and return a {@link com.luaj.vm2.LuaValue} result.
	 * @param lhs Left-hand-side of the division.
	 * @param rhs Right-hand-side of the division.
	 * @return {@link com.luaj.vm2.LuaValue} for the result of the division,
	 * taking into account positive and negiative infinity, and Nan
	 * @see #ddiv_d(double, double) 
	 */
	public static com.luaj.vm2.LuaValue ddiv(double lhs, double rhs) {
		return rhs!=0? valueOf( lhs / rhs ): lhs>0? POSINF: lhs==0? NAN: NEGINF;	
	}
	
	/** Divide two double numbers according to lua math, and return a double result.
	 * @param lhs Left-hand-side of the division.
	 * @param rhs Right-hand-side of the division.
	 * @return Value of the division, taking into account positive and negative infinity, and Nan
	 * @see #ddiv(double, double)
	 */
	public static double ddiv_d(double lhs, double rhs) {
		return rhs!=0? lhs / rhs: lhs>0? Double.POSITIVE_INFINITY: lhs==0? Double.NaN: Double.NEGATIVE_INFINITY;	
	}
	
	/** Take modulo double numbers according to lua math, and return a {@link com.luaj.vm2.LuaValue} result.
	 * @param lhs Left-hand-side of the modulo.
	 * @param rhs Right-hand-side of the modulo.
	 * @return {@link com.luaj.vm2.LuaValue} for the result of the modulo,
	 * using lua's rules for modulo
	 * @see #dmod_d(double, double) 
	 */
	public static com.luaj.vm2.LuaValue dmod(double lhs, double rhs) {
		return rhs!=0? valueOf( lhs-rhs*Math.floor(lhs/rhs) ): NAN;
	}

	/** Take modulo for double numbers according to lua math, and return a double result.
	 * @param lhs Left-hand-side of the modulo.
	 * @param rhs Right-hand-side of the modulo.
	 * @return double value for the result of the modulo, 
	 * using lua's rules for modulo
	 * @see #dmod(double, double)
	 */
	public static double dmod_d(double lhs, double rhs) {
		return rhs!=0? lhs-rhs*Math.floor(lhs/rhs): Double.NaN;
	}

	// relational operators
	public com.luaj.vm2.LuaValue lt(com.luaj.vm2.LuaValue rhs )         { return rhs instanceof LuaNumber ? (rhs.gt_b(v)? TRUE: FALSE) : super.lt(rhs); }
	public com.luaj.vm2.LuaValue lt(double rhs )      { return v < rhs? TRUE: FALSE; }
	public com.luaj.vm2.LuaValue lt(long rhs )         { return v < rhs? TRUE: FALSE; }
	public boolean lt_b( com.luaj.vm2.LuaValue rhs )       { return rhs instanceof LuaNumber ? rhs.gt_b(v) : super.lt_b(rhs); }
	public boolean lt_b( long rhs )         { return v < rhs; }
	public boolean lt_b( double rhs )      { return v < rhs; }
	public com.luaj.vm2.LuaValue lteq(com.luaj.vm2.LuaValue rhs )       { return rhs instanceof LuaNumber ? (rhs.gteq_b(v)? TRUE: FALSE) : super.lteq(rhs); }
	public com.luaj.vm2.LuaValue lteq(double rhs )    { return v <= rhs? TRUE: FALSE; }
	public com.luaj.vm2.LuaValue lteq(long rhs )       { return v <= rhs? TRUE: FALSE; }
	public boolean lteq_b( com.luaj.vm2.LuaValue rhs )     { return rhs instanceof LuaNumber ? rhs.gteq_b(v) : super.lteq_b(rhs); }
	public boolean lteq_b( long rhs )       { return v <= rhs; }
	public boolean lteq_b( double rhs )    { return v <= rhs; }
	public com.luaj.vm2.LuaValue gt(com.luaj.vm2.LuaValue rhs )         { return rhs instanceof LuaNumber ? (rhs.lt_b(v)? TRUE: FALSE) : super.gt(rhs); }
	public com.luaj.vm2.LuaValue gt(double rhs )      { return v > rhs? TRUE: FALSE; }
	public com.luaj.vm2.LuaValue gt(long rhs )         { return v > rhs? TRUE: FALSE; }
	public boolean gt_b( com.luaj.vm2.LuaValue rhs )       { return rhs instanceof LuaNumber ? rhs.lt_b(v) : super.gt_b(rhs); }
	public boolean gt_b( long rhs )         { return v > rhs; }
	public boolean gt_b( double rhs )      { return v > rhs; }
	public com.luaj.vm2.LuaValue gteq(com.luaj.vm2.LuaValue rhs )       { return rhs instanceof LuaNumber ? (rhs.lteq_b(v)? TRUE: FALSE) : super.gteq(rhs); }
	public com.luaj.vm2.LuaValue gteq(double rhs )    { return v >= rhs? TRUE: FALSE; }
	public com.luaj.vm2.LuaValue gteq(long rhs )       { return v >= rhs? TRUE: FALSE; }
	public boolean gteq_b( com.luaj.vm2.LuaValue rhs )     { return rhs instanceof LuaNumber ? rhs.lteq_b(v) : super.gteq_b(rhs); }
	public boolean gteq_b( long rhs )       { return v >= rhs; }
	public boolean gteq_b( double rhs )    { return v >= rhs; }
	
	// string comparison
	public int strcmp( LuaString rhs )      { typerror("attempt to compare number with string"); return 0; }
			
	public String tojstring() {
		/*
		if ( v == 0.0 ) { // never occurs in J2me 
			long bits = Double.doubleToLongBits( v );
			return ( bits >> 63 == 0 ) ? "0" : "-0";
		}
		*/
		long l = (long) v;
		if ( l == v ) 
			return Long.toString(l);
		if ( Double.isNaN(v) )
			return JSTR_NAN;
		if ( Double.isInfinite(v) ) 
			return (v<0? JSTR_NEGINF: JSTR_POSINF);
		return Double.toString(v);
	}
	
	public LuaString strvalue() {
		return LuaString.valueOf(tojstring());
	}
	
	public LuaString optstring(LuaString defval) {
		return LuaString.valueOf(tojstring());
	}
		
	public com.luaj.vm2.LuaValue tostring() {
		return LuaString.valueOf(tojstring());
	}
	
	public String optjstring(String defval) {
		return tojstring();
	}
	
	public LuaNumber optnumber(LuaNumber defval) {
		return this; 
	}
	
	public boolean isnumber() {
		return true; 
	}
	
	public boolean isstring() {
		return true;
	}
	
	public LuaValue tonumber() {
		return this;
	}
	public int checkint()                { return (int) (long) v; }
	public long checklong()              { return (long) v; }
	public LuaNumber checknumber()       { return this; }
	public double checkdouble()          { return v; }
	
	public String checkjstring() { 
		return tojstring();
	}
	public LuaString checkstring() { 
		return LuaString.valueOf(tojstring());
	}
	
	public boolean isvalidkey() {
		return !Double.isNaN(v);
	}	
}
