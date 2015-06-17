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

package xxl.core.cursors.sources;

import xxl.core.cursors.AbstractCursor;
import xxl.core.util.random.DiscreteRandomWrapper;

/**
 * A cursor providing a finite or finite stream of randomly distributed integer
 * values given by the wrapped <i>pseudo random number generator</i>
 * {@link xxl.core.util.random.DiscreteRandomWrapper} for discrete
 * numbers. XXL provides two kinds of PRNG, namley, the first based on
 * {@link java.util.Random Java}'s random number generator and the second based
 * on Colt's random number generator
 * (<tt>cern.jet.random.AbstractDiscreteDistribution</tt>).
 * 
 * <p><b>Example usage (1):</b>
 * <pre>
 *     DiscreteRandomNumber drn = new DiscreteRandomNumber(
 *         new JavaDiscreteRandomWrapper(),
 *         200
 *     );
 * 
 *     drn.open();
 * 
 *     long i = 0;
 *     while (drn .hasNext())
 *         System.out.println((i++) + "\t:\t" + drn.next());
 *     System.out.println();
 * 
 *     drn.close();
 * </pre>
 * This example produces a finite stream of randomly distributed integer values
 * delivered by a {@link xxl.core.util.random.JavaDiscreteRandomWrapper PRNG},
 * i.e., Java's random number generator is used for the provision of randomly
 * distributed integer values. The returned stream of integer objects contains
 * 200 elements as specified in the constructor.</p>
 * 
 * @see java.util.Iterator
 * @see xxl.core.cursors.Cursor
 * @see xxl.core.cursors.AbstractCursor
 * @see java.util.Random
 */
public class DiscreteRandomNumber extends AbstractCursor {

	/**
	 * The random number generator wrapper delivering integer values according to
	 * the wrapped PRNG.
	 */
	protected DiscreteRandomWrapper discreteRandomWrapper;

	/**
	 * A flag signaling if the returned stream of integer values should be
	 * infinite.
	 */
	protected boolean infiniteStream;

	/**
	 * If the returned stream of integer elements is finite, this long value
	 * defines the number of elements to be returned.
	 */
	protected long numberOfElements;

	/**
	 * An internal used counter counting the elements that are yet returned by
	 * this iteration.
	 */
	protected long count;

	/**
	 * Constructs a new discrete random number cursor delivering an
	 * <b>infinite</b> stream of randomly distributed integer values.
	 *
	 * @param discreteRandomWrapper the random number generator wrapper to be
	 *        used for the provision of random integer values.
	 */
	public DiscreteRandomNumber(DiscreteRandomWrapper discreteRandomWrapper) {
		this.discreteRandomWrapper = discreteRandomWrapper;
		this.infiniteStream = true;
		this.numberOfElements = 0;
		this.count = 0;
	}

	/**
	 * Constructs a new discrete random number cursor delivering a <b>finite</b>
	 * stream of randomly distributed integer values.
	 *
	 * @param discreteRandomWrapper the random number generator wrapper to be
	 *        used for the provision of random integer values.
	 * @param numberOfElements the number of elements to be returned.
	 */
	public DiscreteRandomNumber(DiscreteRandomWrapper discreteRandomWrapper, long numberOfElements) {
		this.discreteRandomWrapper = discreteRandomWrapper;
		this.infiniteStream = false;
		this.numberOfElements = numberOfElements;
		this.count = 0;
	}

	/**
	 * Returns <tt>true</tt> if the iteration has more elements. (In other
	 * words, returns <tt>true</tt> if <tt>next</tt> or <tt>peek</tt> would
	 * return an element rather than throwing an exception.)
	 * 
	 * @return <tt>true</tt> if the discrete random number cursor has more
	 *         elements.
	 */
	protected boolean hasNextObject() {
		return infiniteStream || ++count <= numberOfElements;
	}

	/**
	 * Returns the next element in the iteration. This element will be accessible
	 * by some of the discrete random number cursor's methods, e.g.,
	 * <tt>update</tt> or <tt>remove</tt>, until a call to <tt>next</tt> or
	 * <tt>peek</tt> occurs. This is calling <tt>next</tt> or <tt>peek</tt>
	 * proceeds the iteration and therefore its previous element will not be
	 * accessible any more.
	 * 
	 * @return the next element in the iteration.
	 */
	protected Object nextObject() {
		return new Integer(discreteRandomWrapper.nextInt());
	}

	/**
	 * Resets the discrete random number cursor to its initial state such that
	 * the caller is able to traverse the iteration again without constructing a
	 * new discrete random number cursor (optional operation).
	 * 
	 * <p>Note, that this operation is optional and might not work for all
	 * cursors.</p>
	 *
	 * @throws UnsupportedOperationException if the <tt>reset</tt> operation is
	 *         not supported by the discrete random number cursor.
	 */
	public void reset() {
		super.reset();
		this.count = 0;
	}
	
	/**
	 * Returns <tt>true</tt> if the <tt>reset</tt> operation is supported by
	 * the discrete random number cursor. Otherwise it returns <tt>false</tt>.
	 *
	 * @return <tt>true</tt> if the <tt>reset</tt> operation is supported by
	 *         the discrete random number cursor, otherwise <tt>false</tt>.
	 */
	public boolean supportsReset() {
		return true;
	}

	/**
	 * The main method contains some examples to demonstrate the usage and the
	 * functionality of this class.
	 *
	 * @param args array of <tt>String</tt> arguments. It can be used to submit
	 *        parameters when the main method is called.
	 */
	public static void main(String[] args) {

		/*********************************************************************/
		/*                            Example 1                              */
		/*********************************************************************/
		
		DiscreteRandomNumber drn = new DiscreteRandomNumber(
			new xxl.core.util.random.JavaDiscreteRandomWrapper(),
			200
		);
		
		drn.open();
		
		long i = 0;
		while (drn.hasNext())
			System.out.println((i++) + "\t:\t" + drn.next());
		System.out.println();
		
		drn.close();
	}
}
