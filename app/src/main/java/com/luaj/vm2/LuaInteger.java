/*******************************************************************************
* Copyright (c) 2009 Luaj.com. All rights reserved.
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


import com.luaj.vm2.LuaDouble;
import com.luaj.vm2.LuaNumber;
import com.luaj.vm2.LuaString;
import com.luaj.vm2.LuaValue;

/**
 * Extension of {@link com.luaj.vm2.LuaNumber} which can hold a Java int as its value.
 * <p>
 * These instance are not instantiated directly by clients, but indirectly 
 * via the static functions {@link com.luaj.vm2.LuaValue#valueOf(int)} or {@link com.luaj.vm2.LuaValue#valueOf(double)}
 * functions.  This ensures that policies regarding pooling of instances are 
 * encapsulated.  
 * <p>
 * There are no API's specific to LuaInteger that are useful beyond what is already 
 * exposed in {@link com.luaj.vm2.LuaValue}.
 * 
 * @see com.luaj.vm2.LuaValue
 * @see com.luaj.vm2.LuaNumber
 * @see com.luaj.vm2.LuaDouble
 * @see com.luaj.vm2.LuaValue#valueOf(int)
 * @see com.luaj.vm2.LuaValue#valueOf(double)
 */
public class LuaInteger extends com.luaj.vm2.LuaNumber {

	private static final LuaInteger[] intValues = new LuaInteger[512];
	static {
		for ( int i=0; i<512; i++ )
			intValues[i] = new LuaInteger(i-256);
	}

	public static LuaInteger valueOf(int i) {
		return i<=255 && i>=-256? intValues[i+256]: new LuaInteger(i);
	};
	
	 // TODO consider moving this to LuaValue
	/** Return a LuaNumber that represents the value provided
	 * @param i long value to represent.
	 * @return LuaNumber that is eithe LuaInteger or LuaDouble representing l
	 * @see com.luaj.vm2.LuaValue#valueOf(int)
	 * @see com.luaj.vm2.LuaValue#valueOf(double)
	 */
	public static com.luaj.vm2.LuaNumber valueOf(long i) {
		return (i<Long.MAX_VALUE||i>Long.MIN_VALUE)? (i<=255 && i>=-256? intValues[(int) (i+256)]:
			(com.luaj.vm2.LuaNumber) new LuaInteger(i)):
			(com.luaj.vm2.LuaNumber) com.luaj.vm2.LuaDouble.valueOf(i);
	}
	
	/** The value being held by this instance. */
	public final long v;
	
	/** 
	 * Package protected constructor. 
	 * @see com.luaj.vm2.LuaValue#valueOf(int)
	 **/
	LuaInteger(long i) {
		this.v = i;
	}
	
	public boolean isint() {		return true;	}
	public boolean isinttype() {	return true;	}
	public boolean islong() {		return true;	}
	
	public byte    tobyte()        { return (byte) v; }
	public char    tochar()        { return (char) v; }
	public double  todouble()      { return v; }
	public float   tofloat()       { return v; }
	public int     toint()         { return (int) v; }
	public long    tolong()        { return v; }
	public short   toshort()       { return (short) v; }

	public double      optdouble(double defval)            { return v; }
	public int         optint(int defval)                  { return (int) v;  }
	public LuaInteger  optinteger(LuaInteger defval)       { return this; }
	public long        optlong(long defval)                { return v; }

	public String tojstring() {
		return Long.toString(v);
	}

	public com.luaj.vm2.LuaString strvalue() {
		return com.luaj.vm2.LuaString.valueOf(Long.toString(v));
	}
		
	public com.luaj.vm2.LuaString optstring(com.luaj.vm2.LuaString defval) {
		return com.luaj.vm2.LuaString.valueOf(Long.toString(v));
	}
	
	public com.luaj.vm2.LuaValue tostring() {
		return com.luaj.vm2.LuaString.valueOf(Long.toString(v));
	}
		
	public String optjstring(String defval) { 
		return Long.toString(v);
	}
	
	public LuaInteger checkinteger() {
		return this;
	}
	
	public boolean isstring() {
		return true;
	}
	
	public int hashCode() {
		return Long.valueOf(v).hashCode();
	}

	public static int hashCode(int x) {
		return x;
	}

	// unary operators
	public com.luaj.vm2.LuaValue neg() { return valueOf(-(long)v); }
	
	// object equality, used for key comparison
	public boolean equals(Object o) { return o instanceof LuaInteger? ((LuaInteger)o).v == v: false; }
	
	// equality w/ metatable processing
	public com.luaj.vm2.LuaValue eq(com.luaj.vm2.LuaValue val )    { return val.raweq(v)? TRUE: FALSE; }
	public boolean eq_b( com.luaj.vm2.LuaValue val )   { return val.raweq(v); }
	
	// equality w/o metatable processing
	public boolean raweq( com.luaj.vm2.LuaValue val )  { return val.raweq(v); }
	public boolean raweq( double val )    { return v == val; }
	public boolean raweq( long val )       { return v == val; }
	
	// arithmetic operators
	public com.luaj.vm2.LuaValue add(com.luaj.vm2.LuaValue rhs )        { return rhs.add(v); }
	public com.luaj.vm2.LuaValue add(double lhs )     { return com.luaj.vm2.LuaDouble.valueOf(lhs + v); }
	public com.luaj.vm2.LuaValue add(long lhs )        { return LuaInteger.valueOf(lhs + (long)v); }
	public com.luaj.vm2.LuaValue sub(com.luaj.vm2.LuaValue rhs )        { return rhs.subFrom(v); }
	public com.luaj.vm2.LuaValue sub(double rhs )        { return com.luaj.vm2.LuaDouble.valueOf(v - rhs); }
	public com.luaj.vm2.LuaValue sub(long rhs )        { return com.luaj.vm2.LuaDouble.valueOf(v - rhs); }
	public com.luaj.vm2.LuaValue subFrom(double lhs )   { return com.luaj.vm2.LuaDouble.valueOf(lhs - v); }
	public com.luaj.vm2.LuaValue subFrom(long lhs )      { return LuaInteger.valueOf(lhs - (long)v); }
	public com.luaj.vm2.LuaValue mul(com.luaj.vm2.LuaValue rhs )        { return rhs.mul(v); }
	public com.luaj.vm2.LuaValue mul(double lhs )   { return com.luaj.vm2.LuaDouble.valueOf(lhs * v); }
	public com.luaj.vm2.LuaValue mul(long lhs )      { return LuaInteger.valueOf(lhs * (long)v); }
	public com.luaj.vm2.LuaValue pow(com.luaj.vm2.LuaValue rhs )        { return rhs.powWith(v); }
	public com.luaj.vm2.LuaValue pow(double rhs )        { return com.luaj.vm2.LuaNumber.valueOf(Math.pow(v,rhs)); }
	public com.luaj.vm2.LuaValue pow(long rhs )        { return com.luaj.vm2.LuaNumber.valueOf(Math.pow(v,rhs)); }
	public com.luaj.vm2.LuaValue powWith(double lhs )   { return com.luaj.vm2.LuaNumber.valueOf(Math.pow(lhs,v)); }
	public com.luaj.vm2.LuaValue powWith(long lhs )      { return com.luaj.vm2.LuaNumber.valueOf(Math.pow(lhs,v)); }
	public com.luaj.vm2.LuaValue div(com.luaj.vm2.LuaValue rhs )        { return rhs.divInto(v); }
	public com.luaj.vm2.LuaValue div(double rhs )        { return com.luaj.vm2.LuaDouble.ddiv(v,rhs); }
	public com.luaj.vm2.LuaValue div(long rhs )        { return com.luaj.vm2.LuaDouble.ddiv(v,rhs); }
	public com.luaj.vm2.LuaValue divInto(double lhs )   { return com.luaj.vm2.LuaDouble.ddiv(lhs,v); }
	public com.luaj.vm2.LuaValue mod(com.luaj.vm2.LuaValue rhs )        { return rhs.modFrom(v); }
	public com.luaj.vm2.LuaValue mod(double rhs )        { return com.luaj.vm2.LuaDouble.dmod(v,rhs); }
	public com.luaj.vm2.LuaValue mod(long rhs )        { return com.luaj.vm2.LuaDouble.dmod(v,rhs); }
	public com.luaj.vm2.LuaValue modFrom(double lhs )   { return LuaDouble.dmod(lhs,v); }

	public com.luaj.vm2.LuaValue idiv(com.luaj.vm2.LuaValue rhs )        { return rhs.idiv(v); }
	public com.luaj.vm2.LuaValue idiv(long lhs )     { return LuaInteger.valueOf(lhs / v); }

	public com.luaj.vm2.LuaValue band(com.luaj.vm2.LuaValue rhs )        { return rhs.band(v); }
	public com.luaj.vm2.LuaValue band(long lhs )     { return LuaInteger.valueOf(lhs & v); }

	public com.luaj.vm2.LuaValue bor(com.luaj.vm2.LuaValue rhs )        { return rhs.bor(v); }
	public com.luaj.vm2.LuaValue bor(long lhs )     { return LuaInteger.valueOf(lhs | v); }

	public com.luaj.vm2.LuaValue bxor(com.luaj.vm2.LuaValue rhs )        { return rhs.bxor(v); }
	public com.luaj.vm2.LuaValue bxor(long lhs )     { return LuaInteger.valueOf(lhs ^ v); }

	public com.luaj.vm2.LuaValue shl(com.luaj.vm2.LuaValue rhs )        { return rhs.shl(v); }
	public com.luaj.vm2.LuaValue shl(long lhs )     { return LuaInteger.valueOf(lhs << v); }

	public com.luaj.vm2.LuaValue shr(com.luaj.vm2.LuaValue rhs )        { return rhs.shr(v); }
	public com.luaj.vm2.LuaValue shr(long lhs )     { return LuaInteger.valueOf(lhs >> v); }

	public com.luaj.vm2.LuaValue bnot()     { return LuaInteger.valueOf( ~v); }

	// relational operators
	public com.luaj.vm2.LuaValue lt(com.luaj.vm2.LuaValue rhs )         { return rhs instanceof com.luaj.vm2.LuaNumber ? (rhs.gt_b(v)? TRUE: FALSE) : super.lt(rhs); }
	public com.luaj.vm2.LuaValue lt(double rhs )      { return v < rhs? TRUE: FALSE; }
	public com.luaj.vm2.LuaValue lt(long rhs )         { return v < rhs? TRUE: FALSE; }
	public boolean lt_b( com.luaj.vm2.LuaValue rhs )       { return rhs instanceof com.luaj.vm2.LuaNumber ? rhs.gt_b(v) : super.lt_b(rhs); }
	public boolean lt_b( long rhs )         { return v < rhs; }
	public boolean lt_b( double rhs )      { return v < rhs; }
	public com.luaj.vm2.LuaValue lteq(com.luaj.vm2.LuaValue rhs )       { return rhs instanceof com.luaj.vm2.LuaNumber ? (rhs.gteq_b(v)? TRUE: FALSE) : super.lteq(rhs); }
	public com.luaj.vm2.LuaValue lteq(double rhs )    { return v <= rhs? TRUE: FALSE; }
	public com.luaj.vm2.LuaValue lteq(long rhs )       { return v <= rhs? TRUE: FALSE; }
	public boolean lteq_b( com.luaj.vm2.LuaValue rhs )     { return rhs instanceof com.luaj.vm2.LuaNumber ? rhs.gteq_b(v) : super.lteq_b(rhs); }
	public boolean lteq_b( long rhs )       { return v <= rhs; }
	public boolean lteq_b( double rhs )    { return v <= rhs; }
	public com.luaj.vm2.LuaValue gt(com.luaj.vm2.LuaValue rhs )         { return rhs instanceof com.luaj.vm2.LuaNumber ? (rhs.lt_b(v)? TRUE: FALSE) : super.gt(rhs); }
	public com.luaj.vm2.LuaValue gt(double rhs )      { return v > rhs? TRUE: FALSE; }
	public com.luaj.vm2.LuaValue gt(long rhs )         { return v > rhs? TRUE: FALSE; }
	public boolean gt_b( com.luaj.vm2.LuaValue rhs )       { return rhs instanceof com.luaj.vm2.LuaNumber ? rhs.lt_b(v) : super.gt_b(rhs); }
	public boolean gt_b( long rhs )         { return v > rhs; }
	public boolean gt_b( double rhs )      { return v > rhs; }
	public com.luaj.vm2.LuaValue gteq(com.luaj.vm2.LuaValue rhs )       { return rhs instanceof com.luaj.vm2.LuaNumber ? (rhs.lteq_b(v)? TRUE: FALSE) : super.gteq(rhs); }
	public com.luaj.vm2.LuaValue gteq(double rhs )    { return v >= rhs? TRUE: FALSE; }
	public com.luaj.vm2.LuaValue gteq(long rhs )       { return v >= rhs? TRUE: FALSE; }
	public boolean gteq_b( LuaValue rhs )     { return rhs instanceof LuaNumber ? rhs.lteq_b(v) : super.gteq_b(rhs); }
	public boolean gteq_b( long rhs )       { return v >= rhs; }
	public boolean gteq_b( double rhs )    { return v >= rhs; }
	
	// string comparison
	public int strcmp( com.luaj.vm2.LuaString rhs )      { typerror("attempt to compare number with string"); return 0; }
	
	public int checkint() { 
		return (int) v;
	}
	public long checklong() {
		return v; 
	}
	public double checkdouble() {
		return v;
	}
	public String checkjstring() { 
		return String.valueOf(v); 
	}
	public LuaString checkstring() {
		return (LuaString) valueOf( String.valueOf(v) );
	}

}
