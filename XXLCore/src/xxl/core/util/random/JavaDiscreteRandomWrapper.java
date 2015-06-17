/* XXL: The eXtensible and fleXible Library for data processing

Copyright (C) 2000-2004 Prof. Dr. Bernhard Seeger
                        Head of the Database Research Group
                        Department of Mathematics and Computer Science
                        University of Marburg
                        Germany

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307,
USA

	http://www.xxl-library.de

bugs, requests for enhancements: request@xxl-library.de

If you want to be informed on new versions of XXL you can 
subscribe to our mailing-list. Send an email to 
	
	xxl-request@lists.uni-marburg.de

without subject and the word "subscribe" in the message body. 
*/

package xxl.core.util.random;

import java.lang.Math;
import java.util.Random;

/**
 *	Default implementation of RandomWrapper-interface. This class
 *	uses the Random-generator provided by Java.
 *	@see java.util.Random
 */

public class JavaDiscreteRandomWrapper implements DiscreteRandomWrapper{

	/** internally used pseudo random number generator */
	protected Random random;

	/** Constructs a new wrapper for the given Random object.
	 * @param random pseudo random number generator.
	 */
	public JavaDiscreteRandomWrapper( Random random){
		this.random = random;
	}

	/** Constructs a new wrapper for the pseudo
	 * random number generator provided by java.
	 */
	public JavaDiscreteRandomWrapper(){
		this( new Random() );
	}

	/** Constructs a new wrapper for the pseudo
	 * random number generator provided by java using the given seed.
	 * @param seed parameter for random number generator 
	 */
	public JavaDiscreteRandomWrapper( long seed){
		this( new Random( seed));
	}

	/** Returns the next pseudo-random, uniformly distributed integer value
	 * between 0.0 and Integer.MAX_VALUE from the java
	 * random number generator's sequence.
	 * The method nextInt is implemented as follows: 
	 * <br><br>
	 * <code><pre>
	public int nextInt(){
		return Math.abs(random.nextInt());
	}
	 * </code></pre><br>
	 * @return the next pseudorandom, uniformly distributed
	 * integer value between 0 and {@link java.lang.Integer#MAX_VALUE Integer.MAX_VALUE}
	 * from the java random number generator's sequence.
	 * @see java.util.Random#nextInt() nextInt()
	 * @see xxl.core.util.random.DiscreteRandomWrapper
	 */
	public int nextInt(){
		return Math.abs( random.nextInt());
	}
}