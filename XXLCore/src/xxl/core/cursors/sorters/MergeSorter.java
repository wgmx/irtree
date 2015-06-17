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

package xxl.core.cursors.sorters;

import java.util.Comparator;
import java.util.Iterator;

import xxl.core.collections.queues.DynamicHeap;
import xxl.core.collections.queues.ListQueue;
import xxl.core.collections.queues.Queue;
import xxl.core.collections.queues.Queues;
import xxl.core.comparators.ComparableComparator;
import xxl.core.comparators.FeatureComparator;
import xxl.core.cursors.AbstractCursor;
import xxl.core.cursors.Cursor;
import xxl.core.cursors.Cursors;
import xxl.core.cursors.groupers.ReplacementSelection;
import xxl.core.cursors.groupers.SortBasedGrouper;
import xxl.core.cursors.mappers.Mapper;
import xxl.core.cursors.unions.Merger;
import xxl.core.cursors.wrappers.QueueCursor;
import xxl.core.functions.Function;
import xxl.core.functions.Println;
import xxl.core.predicates.Predicate;

/**
 * An external merge-sort algorithm based on the
 * {@link xxl.core.cursors.groupers.ReplacementSelection replacement-selection}
 * operator. The external sorting (i.e., the "logical" sort-operator) can be
 * implemented (with "physical" operators) in two ways:
 * <ol>
 *     <li>
 *         Create sorted runs and merge them recursively (external merge-sort) or
 *     </li>
 *     <li>
 *         divide the input recursively into partitions until each partition fits
 *         into main memory. Then the sort-operation is performed with a
 *         main-memory sorting-algorithm (external distribution-sort).
 *     </li>
 * </ol>
 * This class implements the first algorithm. This technique is described in
 * "G. Graefe: <i>Query evaluation techniques for large databases</i>. Computing
 * Surveys 25:2 June 1993."<br> In the open-phase, sorted runs are created with
 * replacement-selection. Note, that replacement-selection produces runs that are
 * on average twice as large as the memory available, or even bigger. Then, the
 * runs (queues) are recursively merged until <tt>finalFanIn</tt> intermediate
 * runs are left. In the next-phase, the remaining runs are merged on demand,
 * i.e., no final queue containing the complete sorted output will be created.
 * The next element is determined when the user calls the <tt>next</tt> method.
 * 
 * <p><b>Example usage (1):</b>
 * <pre>
 *     // create 300.000 random Integers in the range [0;100.000[
 * 
 *     Iterator input = new RandomIntegers(100000, 300000);
 * 
 *     // and sort them where
 *     // input is the input to be sorted
 *     // 12 is the size of an Object of the input
 *     // 12*4096 is the memory available during the open-phase
 *     // 4*4096 is the memory available during the next-phase
 * 
 *     Sorter sorter = new MergeSorter(input, 12, 12*4096, 4*4096);
 * 
 *     sorter.open();
 * 
 *     while (sorter.hasNext())
 *         System.out.println(sorter.next());
 * 
 *     sorter.close();
 * </pre></p>
 * 
 * <p><b>Example usage (2):</b>
 * <pre>
 *     	//sort input1
 * 
 *     Sorter sorter1 = new MergeSorter(input1, 12, 12*4096, 4*4096);
 * 
 *     	//sort input2
 * 
 *     Sorter sorter2 = new MergeSorter(input2, 12, 12*4096, 4*4096);
 * 
 *     //join the inputs
 *     
 *     Cursor join = new SortMergeJoin(sorter1, sorter2, ...);
 * 
 *     join.open();
 * 
 *     while (join.hasNext())
 *         System.out.println(join.next());
 * 
 *     join.close();
 * </pre></p>
 * 
 * <p>The constructor of the merge-sorter at least needs to know the object-size
 * (which can be estimated by summing up the sizes of primitive types, references
 * are assumed to occupy 8 bytes), the memory available during the open-phase and
 * the memory available during the next-phase. The latter values can differ,
 * e.g., for a sort-merge join for the inputs <tt>R</tt> and <tt>S</tt> it is a
 * sensible choice to allow the merge-sorters to use the entire memory in the
 * open-phase. In the next-phase however, two merge-sorters and the join operator
 * have to share the memory and the memory has to be divided, i.e., in the
 * next-phase a merge-sorter typically has less memory available than in the
 * open-phase (see example 2). Further options of more advanced constructors
 * determine how the memory assigned to the merge-sorter is used internally.
 * There are four values:
 * <ul>
 *     <li>
 *         <tt>firstOutputBufferRatio</tt> the ratio of memory available to the
 *         output buffer during run-creation
 *         <dl>
 *             <dt>
 *                 0.0
 *             </dt>
 *             <dd>
 *                 use only one page for the output buffer and what remains is
 *                 used for the heap
 *             </dd>
 *             <dt>
 *                 1.0
 *             </dt>
 *             <dd>
 *                 use as much memory as possible for the output buffer
 *             </dd>
 *         </dl>
 *     </li>
 *     <li>
 *         <tt>outputBufferRatio</tt> the amount of memory available to the
 *         output buffer during intermediate merges (not the final merge)
 *         <dl>
 *             <dt>
 *                 0.0
 *             </dt>
 *             <dd>
 *                 use only one page for the output buffer, what remains is used
 *                 for the merger and the input buffer, <tt>inputBufferRatio</tt>
 *                 determines how the remaining memory is distributed between
 *                 them
 *             </dd>
 *             <dt>
 *                 1.0
 *             </dt>
 *             <dd>
 *                 use as much memory as possible for the output buffer
 *             </dd>
 *         </dl>
 *     </li>
 *     <li>
 *         <tt>inputBufferRatio</tt> the amount of memory available to the input
 *         buffer during intermediate merges (not the final merge)
 *         <dl>
 *             <dt>
 *                 0.0
 *             </dt>
 *             <dd>
 *                 use only one page for the input buffer, what remains is used
 *                 for the merger (maximal fan-in)
 *             </dd>
 *             <dt>
 *                 1.0
 *             </dt>
 *             <dd>
 *                 use as much memory as possible for the input buffer
 *             </dd>
 *         </dl>
 *     </li>
 *     <li>
 *         <tt>finalInputBufferRatio</tt> the amount of memory available to the
 *         input buffers of the final (online) merge
 *         <dl>
 *             <dt>
 *                 0.0
 *             </dt>
 *             <dd>
 *                 maximum number of inputs (maximal fan-in), i.e., perform the
 *                 online merge as early as possible
 *             </dd>
 *             <dt>
 *                 1.0
 *             </dt>
 *             <dd>
 *                 write the entire data into a final queue, the online "merger"
 *                 just reads the data from this queue
 *             </dd>
 *         </dl>
 *     </li>
 * </ul></p>
 * 
 * <p>If you call a constructor with the <tt>verbose</tt>-flag set to
 * <tt>true</tt> the merge-sorter displays how the memory was distributed
 * internally. In addition, the number of merges is displayed.<br />
 * <b>Example Output:</b>
 * <pre>
 * dittrich@hadar:~/newXXL> java -classpath class xxl/cursors/MergeSorter
 * -----------------------------------------------------------
 * GLOBAL
 *         blockSize:              4096
 *         objectSize:             12
 *
 * OPEN phase
 *         memSize:                49152
 *
 *     run creation
 *         firstOutputBufferSize:  4096
 *         heapSize:               3754
 *
 *     intermediate merges
 *         outputBufferSize:       4096
 *         inputBufferSize:        4096
 *         fanIn:                  10
 *
 * NEXT phase
 *     final merge
 *         finalMemSize:           16384
 *         finalInputBufferSize:   4096
 *         finalFanIn:             3
 * -----------------------------------------------------------
 *
 * merge: fanIn: 3  queues.size(): 41 --> 39
 * merge: fanIn: 10  queues.size(): 39 --> 30
 * merge: fanIn: 10  queues.size(): 30 --> 21
 * merge: fanIn: 10  queues.size(): 21 --> 12
 * merge: fanIn: 10  queues.size(): 12 --> 3
 * final merge: fanIn: 3
 * Objects: 300000
 * </pre></p>
 * 
 * <p>Most constructors have a parameter named <tt>newQueue</tt>, that should
 * return a queue, which is used by the algorithm to materialize the internal
 * runs, i.e., this function determines whether the sort operator works on queues
 * based on external storage or in main memory (useful for testing and
 * counting).</p>
 * 
 * <p><b>Example usage (3):</b>
 * <pre>
 *     Function newQueue = new Function() {
 *         public Object invoke(Object function1, Object function2) {
 * 
 *             //return a new ListQueue (resident in main memory)
 * 
 *             return new ListQueue();
 *         }
 *     };
 * </pre>
 * The <tt>invoke</tt> method with two object parameters has to be overriden.
 * This method is called by the merge-sorter with two internal functions whenever
 * a new queue is needed:
 * <pre>
 *     //internal call to newQueue
 * 
 *     newQueue.invoke(getInputBufferSize(), getOutputBufferSize());
 * </pre>
 * The two parameterless functions return an integer object with the buffer size
 * for the input- and output-buffer of the queue. You should pass these functions
 * directly to the queue (if it can make use of these parameters). If the queue
 * gets a read-request it will then open an input stream and call the
 * <tt>getInputBufferSize</tt> method. This means, that the buffer size is
 * determined when it is needed and not when the queue is constructed. In other
 * words, the computation of the buffer size is delayed until the size of the
 * buffer is needed by the queue to construct the buffer.</p>
 *
 * <p><b>Example usage (4):</b>
 * <pre>
 *     Function newQueue = new Function() {
 *         
 *         protected File getFile() {
 *             try {
 * 
 *                 //return a new File-Object (a temp-file created by java.io.File)
 * 
 *                 return File.createTempFile("RAF", ".queue");
 *             }
 *             catch (IOException e) {}
 *         }
 * 
 *         protected Function newObject = new Function() {
 *             public Object invoke() {
 * 
 *                 //return a new Object
 * 
 *                 return new MyType();
 *             }
 *         };
 * 
 *         public Object invoke(Object function1, Object function2) {
 *             return new RandomAccessFileQueue(
 *                 getFile(),
 *                 newObject,
 *                 (Function) function1,
 *                 (Function) function2
 *             );
 *         }
 *     }
 * </pre></p>
 *
 * @see java.util.Iterator
 * @see xxl.core.cursors.Cursor
 * @see xxl.core.cursors.AbstractCursor
 */
public class MergeSorter extends AbstractCursor {

	/**
	 * The input iteration holding the data to be sorted.
	 */
	protected Cursor input;
	
	/**
	 * The comparator used to compare the elements of the two input iteration.
	 */
	protected Comparator comparator;

	/**
	 * The size of a block used for the sort operation.
	 */
	protected int blockSize;

	/**
	 * The size of an used for the sort operation.
	 */
	protected int objectSize;

	/**
	 * The size of the memory used for intermediate merges of the sort operation.
	 */
	protected int memSize;

	/**
	 * The size of the memory used for the final merge of the sort operation.
	 */
	protected int finalMemSize;

	/**
	 * A binary function, that should return a queue, which is used by the
	 * algorithm to materialize the internal runs, i.e., this function determines
	 * whether the sort operator works on queues based on external storage or in
	 * main memory (useful for testing and counting). The <tt>invoke</tt> method
	 * with two object parameters has to be overriden. This method is called by
	 * the merge-sorter with two internal functions whenever a new queue is
	 * needed:
	 * <pre>
	 *     //internal call to newQueue
	 * 
	 *     newQueue.invoke(getInputBufferSize(), getOutputBufferSize());
	 * </pre>
	 */
	protected Function newQueue;

	/**
	 * A binary function, which creates a queue, that should contain the queues
	 * to be merged. The function takes an iterator and the comparator
	 * <tt>queuesQueueComparator</tt> as parameters. E.g.,
	 * <pre>
	 *     new Function() {
	 *         public Object invoke(Object iterator, Object comparator) {
	 *             return new DynamicHeap((Iterator)iterator, (Comparator)comparator);
	 *         }
	 *     };
	 * </pre>
	 * The queues contained in the iterator are inserted in the dynamic heap
	 * using the given comparator for comparison.
	 */
	protected Function newQueuesQueue;

	/**
	 * This comparator determines the next queue used for merging.
	 */
	protected Comparator queuesQueueComparator;		

	/**
	 * A boolean flag that determines whether a debug output should be generated
	 * or not.
	 */
	protected boolean verbose;
	
	/**
	 * A cursor holding the final merge of the merge-sorter. 
	 */
	protected Cursor cursor;

	/**
	 * The input-buffer size is used during the intermediate merges. It is
	 * computed as follows:
	 * <pre>
	 *     inputBufferSize = (((int)(((memSize-outputBufferSize)/2-(objectSize+blockSize))*inputBufferRatio))/blockSize+1)*blockSize;
	 * </pre>
	 */
	protected int inputBufferSize;

	/**
	 * The final input-buffer size is used for the final merge. It is computed as
	 * follows:
	 * <pre>
	 *     finalInputBufferSize = (((int)((finalMemSize-objectSize-blockSize)*finalInputBufferRatio))/blockSize+1)*blockSize;
	 * </pre>
	 */
	protected int finalInputBufferSize;

	/**
	 * Returns the current input-buffer size depending on
	 * <tt>openPhaseFinished</tt>.
	 *
	 * @return a parameterless function delivering the current input-buffer size.
	 */
	protected Function getInputBufferSize() {
		return new Function() {
			public Object invoke() {
				return new Integer(
					openPhaseFinished ?
						finalInputBufferSize :
						((memSize - outputBufferSize) / currentFanIn - objectSize) / blockSize * blockSize
				);
			}
		};
	}

	/**
	 * The heap size used concerning replacement-selection. In the open-phase,
	 * sorted runs are created with replacement-selection. The heap size is
	 * computed as follows:
	 * <pre>
	 *     heapSize = (memSize-firstOutputBufferSize)/objectSize;
	 * </pre>
	 */
	protected int heapSize;

	/**
	 * The first output-buffer size is used during the run creation in the
	 * open-phase. It is computed as follows:
	 * <pre>
	 *     firstOutputBufferSize = (((int)(((memSize-objectSize-blockSize)*firstOutputBufferRatio)))/blockSize+1)*blockSize;
	 * </pre>
	 */
	protected int firstOutputBufferSize;

	/**
	 * The output-buffer size is used during the intermediate merges. It is
	 * computed as follows:
	 * <pre>
	 *     outputBufferSize = (((int)((memSize-blockSize-2*(objectSize+blockSize))*outputBufferRatio))/blockSize+1)*blockSize;
	 * </pre>
	 */
	protected int outputBufferSize;

	/**
	 * Returns the current output-buffer size depending on <tt>runsCreated</tt>.
	 *
	 * @return a parameterless function delivering the current output-buffer
	 *         size.
	 */
	protected Function getOutputBufferSize() {
		return new Function() {
			public Object invoke() {
				return new Integer(
					runsCreated ?
						outputBufferSize + (memSize - outputBufferSize) % (((Integer)getInputBufferSize().invoke()).intValue() + objectSize) / blockSize * blockSize :
						firstOutputBufferSize + (memSize - firstOutputBufferSize) % objectSize / blockSize * blockSize
				);
			}
		};
	}

	/**
	 * The number of currently merged runs (queues).
	 */
	protected int currentFanIn;

	/**
	 * The fan-in is needed as parameter for the intermediate merges. It is
	 * computed as follows:
	 * <pre>
	 *     fanIn = (memSize-outputBufferSize)/(inputBufferSize+objectSize);
	 * </pre>
	 */
	protected int fanIn;

	/**
	 * The sorted runs (queues) are recursively merged until only final fan-in
	 * intermediate runs are left. It is used for the final merge and is computed
	 * as follows:
	 * <pre>
	 *     finalFanIn = finalMemSize/(finalInputBufferSize+objectSize);
	 * </pre>
	 */
	protected int finalFanIn;

	/**
	 * A boolean flag to signal whether the runs are created.
	 */
	protected boolean runsCreated = false;

	/**
	 * A boolean flag to signal whether the open-phase is finished.
	 */
	protected boolean openPhaseFinished = false;
	
	/**
	 * Creates a new merge-sorter.
	 *
	 * @param input the input iteration to be sorted.
	 * @param comparator the comparator used to compare the elements in the heap
	 *        (replacement-selection).
	 * @param blockSize the size of a block (page).
	 * @param objectSize the size of an object in main memory.
	 * @param memSize the memory available to the merge-sorter during the
	 *        open-phase.
	 * @param firstOutputBufferRatio the ratio of memory available to the
	 *        output-buffer during run-creation.
	 *        <dl>
	 *            <dt>
	 *                0.0
	 *            </dt>
	 *            <dd>
	 *                use only one page for the output buffer and what remains is
	 *                used for the heap
	 *            </dd>
	 *            <dt>
	 *                1.0
	 *            </dt>
	 *            <dd>
	 *                use as much memory as possible for the output buffer
	 *            </dd>
	 *        </dl>
	 * @param outputBufferRatio the amount of memory available to the
	 *        output-buffer during intermediate merges (not the final merge).
	 *        <dl>
	 *            <dt>
	 *                0.0
	 *            </dt>
	 *            <dd>
	 *                use only one page for the output buffer, what remains is
	 *                used for the merger and the input buffer,
	 *                <tt>inputBufferRatio</tt> determines how the remaining
	 *                memory is distributed between them
	 *            </dd>
	 *            <dt>
	 *                1.0
	 *            </dt>
	 *            <dd>
	 *                use as much memory as possible for the output buffer
	 *            </dd>
	 *        </dl>
	 * @param inputBufferRatio the amount of memory available to the input-buffer
	 *        during intermediate merges (not the final merge).
	 *        <dl>
	 *            <dt>
	 *                0.0
	 *            </dt>
	 *            <dd>
	 *                use only one page for the input buffer, what remains is
	 *                used for the merger (maximal fan-in)
	 *            </dd>
	 *            <dt>
	 *                1.0
	 *            </dt>
	 *            <dd>
	 *                use as much memory as possible for the input buffer
	 *            </dd>
	 *        </dl>
	 * @param finalMemSize the memory available to the merge-sorter during the
	 *        next-phase.
	 * @param finalInputBufferRatio the amount of memory available to the
	 *        input-buffer of the final (online) merge.
	 *        <dl>
	 *            <dt>
	 *                0.0
	 *            </dt>
	 *            <dd>
	 *                maximum number of inputs (maximal fan-in), i.e., perform
	 *                the online merge as early as possible
	 *            </dd>
	 *            <dt>
	 *                1.0
	 *            </dt>
	 *            <dd>
	 *                write the entire data into a final queue, the online
	 *                "merger" just reads the data from this queue
	 *            </dd>
	 *        </dl>
	 * @param newQueue the function <tt>newQueue</tt> should return a queue,
	 *        which is used by the algorithm to materialize the internal runs,
	 *        i.e., this function determines whether the sort operator works on
	 *        queues based on external storage or in main memory (useful for
	 *        testing and counting). The function takes two parameterless
	 *        functions <tt>getInputBufferSize</tt> and
	 *        <tt>getOutputBufferSize</tt> as parameters.
	 * @param newQueuesQueue if this function is invoked, the queue, that should
	 *        contain the queues to be merged, is returned. The function takes an
	 *        iterator and the comparator <tt>queuesQueueComparator</tt> as
	 *        parameters. E.g.,
	 *        <pre>
	 *            new Function() {
	 *                public Object invoke(Object iterator, Object comparator) {
	 *                    return new DynamicHeap((Iterator)iterator, (Comparator)comparator);
	 *                }
	 *            }
	 *        </pre>
	 *        The queues contained in the iterator are inserted in the dynamic
	 *        heap using the given comparator for comparison.
	 * @param queuesQueueComparator this comparator determines the next queue
	 *        used for merging.
	 * @param verbose if the <tt>verbose</tt> flag set to <tt>true</tt> the
	 *        merge-sorter displays how the memory was distributed internally.
	 *        In addition, the number of merges is displayed.
	 */
	public MergeSorter(
		Iterator input,
		final Comparator comparator,
		final int blockSize,
		final int objectSize,
		final int memSize,
		double firstOutputBufferRatio,
		double outputBufferRatio,
		double inputBufferRatio,
		int finalMemSize,
		double finalInputBufferRatio,
		final Function newQueue,
		Function newQueuesQueue,
		final Comparator queuesQueueComparator,
		final boolean verbose
	) {
		this.input = Cursors.wrap(input);
		this.comparator = comparator;
		this.blockSize = blockSize;
		this.objectSize = objectSize;
		this.memSize = memSize;
		this.finalMemSize = finalMemSize;
		this.newQueue = newQueue;
		this.newQueuesQueue = newQueuesQueue;
		this.queuesQueueComparator = queuesQueueComparator;		
		this.verbose = verbose;

		//run creation:
		this.firstOutputBufferSize = (((int)(((memSize - objectSize - blockSize) * firstOutputBufferRatio))) / blockSize + 1) * blockSize;
		this.heapSize = (memSize - firstOutputBufferSize) / objectSize;

		//intermediate merges:
		this.outputBufferSize = (((int)((memSize - blockSize - 2 * (objectSize + blockSize)) * outputBufferRatio)) / blockSize + 1) * blockSize;
		this.inputBufferSize = (((int)(((memSize - outputBufferSize) / 2 - (objectSize + blockSize)) * inputBufferRatio)) / blockSize + 1) * blockSize;
		this.fanIn = (memSize - outputBufferSize) / (inputBufferSize + objectSize);

		//final merge:
		this.finalInputBufferSize = (((int)((finalMemSize - objectSize - blockSize) * finalInputBufferRatio)) / blockSize + 1) * blockSize;
		this.finalFanIn = finalMemSize / (finalInputBufferSize + objectSize);

		if (verbose)
			showInfo();
	}

	/**
	 * Creates a new merge-sorter using a default
	 * {@link xxl.core.comparators.ComparableComparator comparator} for the
	 * comparison of the input cursors' elements which assumes that these
	 * elements implement the {@link java.lang.Comparable comparable} interface.
	 * The queues to be merged are inserted in a
	 * {@link xxl.core.collections.queues.DynamicHeap dynamic heap} and they are
	 * compared according their sizes.
	 *
	 * @param input the input iteration to be sorted.
	 * @param objectSize the size of an object in main memory.
	 * @param memSize the memory available to the merge-sorter during the
	 *        open-phase.
	 * @param firstOutputBufferRatio the ratio of memory available to the
	 *        output-buffer during run-creation.
	 *        <dl>
	 *            <dt>
	 *                0.0
	 *            </dt>
	 *            <dd>
	 *                use only one page for the output buffer and what remains is
	 *                used for the heap
	 *            </dd>
	 *            <dt>
	 *                1.0
	 *            </dt>
	 *            <dd>
	 *                use as much memory as possible for the output buffer
	 *            </dd>
	 *        </dl>
	 * @param outputBufferRatio the amount of memory available to the
	 *        output-buffer during intermediate merges (not the final merge).
	 *        <dl>
	 *            <dt>
	 *                0.0
	 *            </dt>
	 *            <dd>
	 *                use only one page for the output buffer, what remains is
	 *                used for the merger and the input buffer,
	 *                <tt>inputBufferRatio</tt> determines how the remaining
	 *                memory is distributed between them
	 *            </dd>
	 *            <dt>
	 *                1.0
	 *            </dt>
	 *            <dd>
	 *                use as much memory as possible for the output buffer
	 *            </dd>
	 *        </dl>
	 * @param inputBufferRatio the amount of memory available to the input-buffer
	 *        during intermediate merges (not the final merge).
	 *        <dl>
	 *            <dt>
	 *                0.0
	 *            </dt>
	 *            <dd>
	 *                use only one page for the input buffer, what remains is
	 *                used for the merger (maximal fan-in)
	 *            </dd>
	 *            <dt>
	 *                1.0
	 *            </dt>
	 *            <dd>
	 *                use as much memory as possible for the input buffer
	 *            </dd>
	 *        </dl>
	 * @param finalMemSize the memory available to the merge-sorter during the
	 *        next-phase.
	 * @param finalInputBufferRatio the amount of memory available to the
	 *        input-buffer of the final (online) merge.
	 *        <dl>
	 *            <dt>
	 *                0.0
	 *            </dt>
	 *            <dd>
	 *                maximum number of inputs (maximal fan-in), i.e., perform
	 *                the online merge as early as possible
	 *            </dd>
	 *            <dt>
	 *                1.0
	 *            </dt>
	 *            <dd>
	 *                write the entire data into a final queue, the online
	 *                "merger" just reads the data from this queue
	 *            </dd>
	 *        </dl>
	 * @param newQueue the function <tt>newQueue</tt> should return a queue,
	 *        which is used by the algorithm to materialize the internal runs,
	 *        i.e., this function determines whether the sort operator works on
	 *        queues based on external storage or in main memory (useful for
	 *        testing and counting). The function takes two parameterless
	 *        functions <tt>getInputBufferSize</tt> and
	 *        <tt>getOutputBufferSize</tt> as parameters.
	 * @param verbose if the <tt>verbose</tt> flag set to <tt>true</tt> the
	 *        merge-sorter displays how the memory was distributed internally.
	 *        In addition, the number of merges is displayed.
	 */
	public MergeSorter(
		Iterator input,
		final int objectSize,
		final int memSize,
		double firstOutputBufferRatio,
		double outputBufferRatio,
		double inputBufferRatio,
		int finalMemSize,
		double finalInputBufferRatio,
		final Function newQueue,
		final boolean verbose
	) {
		this(
			input,
			ComparableComparator.DEFAULT_INSTANCE,
			4096,
			objectSize,
			memSize,
			firstOutputBufferRatio,
			outputBufferRatio,
			inputBufferRatio,
			finalMemSize,
			finalInputBufferRatio,
			newQueue,
			new Function() {
				public Object invoke(Object iterator, Object comparator) {
					return new DynamicHeap(
						Cursors.toArray((Iterator)iterator),
						(Comparator)comparator
					);
				}
			},
			//compares two queues according to their sizes
			new FeatureComparator(
				new Function() {
					public Object invoke(Object queueCursor) {
						return new Integer(((QueueCursor)queueCursor).size());
					}
				}
			),
			verbose
		);
	}

	/**
	 * Creates a new merge-sorter using a default
	 * {@link xxl.core.comparators.ComparableComparator comparator} for the
	 * comparison of the input cursors' elements which assumes that these
	 * elements implement the {@link java.lang.Comparable comparable} interface.
	 * The parameters <tt>inputBufferRatio</tt>, <tt>finalInputBufferRatio</tt>,
	 * <tt>firstOutputBufferRatio</tt> and <tt>outputBufferRatio</tt> are set to
	 * <tt>0.0</tt>. That means only one page is reserved for input- and
	 * output-buffer and the maximal fan-in is used during the intermediate
	 * merges and for the final merge. The queues to be merged are inserted in a
	 * {@link xxl.core.collections.queues.DynamicHeap dynamic heap} and they are
	 * compared according their sizes.
	 *
	 * @param input the input iteration to be sorted.
	 * @param objectSize the size of an object in main memory.
	 * @param memSize the memory available to the merge-sorter during the
	 *        open-phase.
	 * @param finalMemSize the memory available to the merge-sorter during the
	 *        next-phase.
	 * @param newQueue the function <tt>newQueue</tt> should return a queue,
	 *        which is used by the algorithm to materialize the internal runs,
	 *        i.e., this function determines whether the sort operator works on
	 *        queues based on external storage or in main memory (useful for
	 *        testing and counting). The function takes two parameterless
	 *        functions <tt>getInputBufferSize</tt> and
	 *        <tt>getOutputBufferSize</tt> as parameters.
	 * @param verbose if the <tt>verbose</tt> flag set to <tt>true</tt> the
	 *        merge-sorter displays how the memory was distributed internally.
	 *        In addition, the number of merges is displayed.
	 */
	public MergeSorter(
		Iterator input,
		final int objectSize,
		final int memSize,
		int finalMemSize,
		final Function newQueue,
		boolean verbose
	) {
		this(
			input,
			objectSize,
			memSize,
			0.0,
			0.0,
			0.0,
			finalMemSize,
			0.0,
			newQueue,
			verbose
		);
	}

	/**
	 * Creates a new merge-sorter using a default
	 * {@link xxl.core.comparators.ComparableComparator comparator} for the
	 * comparison of the input cursors' elements which assumes that these
	 * elements implement the {@link java.lang.Comparable comparable} interface.
	 * The parameters <tt>inputBufferRatio</tt>, <tt>finalInputBufferRatio</tt>,
	 * <tt>firstOutputBufferRatio</tt> and <tt>outputBufferRatio</tt> are set to
	 * <tt>0.0</tt>. That means only one page is reserved for input- and
	 * output-buffer and the maximal fan-in is used during the intermediate
	 * merges and for the final merge. The queue are given by 
	 * {@link xxl.core.collections.queues.ListQueue list-queues}, i.e., the
	 * intermediate runs are materialized in main memory and they will be
	 * inserted in a {@link xxl.core.collections.queues.DynamicHeap dynamic heap}
	 * and they are compared according their sizes.
	 *
	 * @param input the input iteration to be sorted.
	 * @param objectSize the size of an object in main memory.
	 * @param memSize the memory available to the merge-sorter during the
	 *        open-phase.
	 * @param finalMemSize the memory available to the merge-sorter during the
	 *        next-phase.
	 * @param verbose if the <tt>verbose</tt> flag set to <tt>true</tt> the
	 *        merge-sorter displays how the memory was distributed internally.
	 *        In addition, the number of merges is displayed.
	 */
	public MergeSorter(
		Iterator input,
		final int objectSize,
		final int memSize,
		int finalMemSize,
		boolean verbose
	) {
		this(
			input,
			objectSize,
			memSize,
			finalMemSize,
			new Function() {
				public Object invoke(Object function1, Object function2){
					return new ListQueue();
				}
			},
			verbose
		);
	}

	/**
	 * Creates a new merge-sorter using a default
	 * {@link xxl.core.comparators.ComparableComparator comparator} for the
	 * comparison of the input cursors' elements which assumes that these
	 * elements implement the {@link java.lang.Comparable comparable} interface.
	 * The parameters <tt>inputBufferRatio</tt>, <tt>finalInputBufferRatio</tt>,
	 * <tt>firstOutputBufferRatio</tt> and <tt>outputBufferRatio</tt> are set to
	 * <tt>0.0</tt>. That means only one page is reserved for input- and
	 * output-buffer and the maximal fan-in is used during the intermediate
	 * merges and for the final merge. The queues to be merged are inserted in a
	 * {@link xxl.core.collections.queues.DynamicHeap dynamic heap} and they are
	 * compared according their sizes.
	 *
	 * @param input the input iteration to be sorted.
	 * @param objectSize the size of an object in main memory.
	 * @param memSize the memory available to the merge-sorter during the
	 *        open-phase.
	 * @param finalMemSize the memory available to the merge-sorter during the
	 *        next-phase.
	 * @param newQueue the function <tt>newQueue</tt> should return a queue,
	 *        which is used by the algorithm to materialize the internal runs,
	 *        i.e., this function determines whether the sort operator works on
	 *        queues based on external storage or in main memory (useful for
	 *        testing and counting). The function takes two parameterless
	 *        functions <tt>getInputBufferSize</tt> and
	 *        <tt>getOutputBufferSize</tt> as parameters.
	 */
	public MergeSorter(
		Iterator input,
		final int objectSize,
		final int memSize,
		int finalMemSize,
		Function newQueue
	) {
		this(
			input,
			objectSize,
			memSize,
			finalMemSize,
			newQueue,
			false
		);
	}


	/**
	 * Creates a new merge-sorter using a default
	 * {@link xxl.core.comparators.ComparableComparator comparator} for the
	 * comparison of the input cursors' elements which assumes that these
	 * elements implement the {@link java.lang.Comparable comparable} interface.
	 * The parameters <tt>inputBufferRatio</tt>, <tt>finalInputBufferRatio</tt>,
	 * <tt>firstOutputBufferRatio</tt> and <tt>outputBufferRatio</tt> are set to
	 * <tt>0.0</tt>. That means only one page is reserved for input- and
	 * output-buffer and the maximal fan-in is used during the intermediate
	 * merges and for the final merge. The queue are given by 
	 * {@link xxl.core.collections.queues.ListQueue list-queues}, i.e., the
	 * intermediate runs are materialized in main memory and they will be
	 * inserted in a {@link xxl.core.collections.queues.DynamicHeap dynamic heap}
	 * and they are compared according their sizes.
	 *
	 * @param input the input iteration to be sorted.
	 * @param objectSize the size of an object in main memory.
	 * @param memSize the memory available to the merge-sorter during the
	 *        open-phase.
	 * @param finalMemSize the memory available to the merge-sorter during the
	 *        next-phase.
	 */
	public MergeSorter(
		Iterator input,
		final int objectSize,
		final int memSize,
		int finalMemSize
	) {
		this(
			input,
			objectSize,
			memSize,
			finalMemSize,
			false
		);
	}

	/**
	 * Creates a new merge-sorter. The queues to be merged are inserted in a
	 * {@link xxl.core.collections.queues.DynamicHeap dynamic heap} and they are
	 * compared according their sizes.
	 *
	 * @param input the input iteration to be sorted.
	 * @param comparator the comparator used to compare the elements in the heap
	 *        (replacement-selection).
	 * @param objectSize the size of an object in main memory.
	 * @param memSize the memory available to the merge-sorter during the
	 *        open-phase.
	 * @param firstOutputBufferRatio the ratio of memory available to the
	 *        output-buffer during run-creation.
	 *        <dl>
	 *            <dt>
	 *                0.0
	 *            </dt>
	 *            <dd>
	 *                use only one page for the output buffer and what remains is
	 *                used for the heap
	 *            </dd>
	 *            <dt>
	 *                1.0
	 *            </dt>
	 *            <dd>
	 *                use as much memory as possible for the output buffer
	 *            </dd>
	 *        </dl>
	 * @param outputBufferRatio the amount of memory available to the
	 *        output-buffer during intermediate merges (not the final merge).
	 *        <dl>
	 *            <dt>
	 *                0.0
	 *            </dt>
	 *            <dd>
	 *                use only one page for the output buffer, what remains is
	 *                used for the merger and the input buffer,
	 *                <tt>inputBufferRatio</tt> determines how the remaining
	 *                memory is distributed between them
	 *            </dd>
	 *            <dt>
	 *                1.0
	 *            </dt>
	 *            <dd>
	 *                use as much memory as possible for the output buffer
	 *            </dd>
	 *        </dl>
	 * @param inputBufferRatio the amount of memory available to the input-buffer
	 *        during intermediate merges (not the final merge).
	 *        <dl>
	 *            <dt>
	 *                0.0
	 *            </dt>
	 *            <dd>
	 *                use only one page for the input buffer, what remains is
	 *                used for the merger (maximal fan-in)
	 *            </dd>
	 *            <dt>
	 *                1.0
	 *            </dt>
	 *            <dd>
	 *                use as much memory as possible for the input buffer
	 *            </dd>
	 *        </dl>
	 * @param finalMemSize the memory available to the merge-sorter during the
	 *        next-phase.
	 * @param finalInputBufferRatio the amount of memory available to the
	 *        input-buffer of the final (online) merge.
	 *        <dl>
	 *            <dt>
	 *                0.0
	 *            </dt>
	 *            <dd>
	 *                maximum number of inputs (maximal fan-in), i.e., perform
	 *                the online merge as early as possible
	 *            </dd>
	 *            <dt>
	 *                1.0
	 *            </dt>
	 *            <dd>
	 *                write the entire data into a final queue, the online
	 *                "merger" just reads the data from this queue
	 *            </dd>
	 *        </dl>
	 * @param newQueue the function <tt>newQueue</tt> should return a queue,
	 *        which is used by the algorithm to materialize the internal runs,
	 *        i.e., this function determines whether the sort operator works on
	 *        queues based on external storage or in main memory (useful for
	 *        testing and counting). The function takes two parameterless
	 *        functions <tt>getInputBufferSize</tt> and
	 *        <tt>getOutputBufferSize</tt> as parameters.
	 * @param verbose if the <tt>verbose</tt> flag set to <tt>true</tt> the
	 *        merge-sorter displays how the memory was distributed internally.
	 *        In addition, the number of merges is displayed.
	 */
	public MergeSorter(
		Iterator input,
		Comparator comparator,
		final int objectSize,
		final int memSize,
		double firstOutputBufferRatio,
		double outputBufferRatio,
		double inputBufferRatio,
		int finalMemSize,
		double finalInputBufferRatio,
		final Function newQueue,
		final boolean verbose
	) {
		this(
			input,
			comparator,
			4096,
			objectSize,
			memSize,
			firstOutputBufferRatio,
			outputBufferRatio,
			inputBufferRatio,
			finalMemSize,
			finalInputBufferRatio,
			newQueue,
			new Function() {
				public Object invoke(Object iterator, Object comparator) {
					return new DynamicHeap(
						Cursors.toArray((Iterator)iterator),
						(Comparator)comparator
					);
				}
			},
			//compares two queues according to their sizes
			new FeatureComparator(
				new Function() {
					public Object invoke(Object queueCursor) {
						return new Integer(((QueueCursor)queueCursor).size());
					}
				}
			),
			verbose
		);
	}

	/**
	 * Creates a new merge-sorter. The parameters <tt>inputBufferRatio</tt>,
	 * <tt>finalInputBufferRatio</tt>, <tt>firstOutputBufferRatio</tt> and
	 * <tt>outputBufferRatio</tt> are set to <tt>0.0</tt>. That means only one
	 * page is reserved for input- and output-buffer and the maximal fan-in is
	 * used during the intermediate merges and for the final merge. The queues to
	 * be merged are inserted in a
	 * {@link xxl.core.collections.queues.DynamicHeap dynamic heap} and they are
	 * compared according their sizes.
	 *
	 * @param input the input iteration to be sorted.
	 * @param comparator the comparator used to compare the elements in the heap
	 *        (replacement-selection).
	 * @param objectSize the size of an object in main memory.
	 * @param memSize the memory available to the merge-sorter during the
	 *        open-phase.
	 * @param finalMemSize the memory available to the merge-sorter during the
	 *        next-phase.
	 * @param newQueue the function <tt>newQueue</tt> should return a queue,
	 *        which is used by the algorithm to materialize the internal runs,
	 *        i.e., this function determines whether the sort operator works on
	 *        queues based on external storage or in main memory (useful for
	 *        testing and counting). The function takes two parameterless
	 *        functions <tt>getInputBufferSize</tt> and
	 *        <tt>getOutputBufferSize</tt> as parameters.
	 * @param verbose if the <tt>verbose</tt> flag set to <tt>true</tt> the
	 *        merge-sorter displays how the memory was distributed internally.
	 *        In addition, the number of merges is displayed.
	 */
	public MergeSorter(
		Iterator input,
		Comparator comparator,
		final int objectSize,
		final int memSize,
		int finalMemSize,
		final Function newQueue,
		boolean verbose
	) {
		this(
			input,
			comparator,
			objectSize,
			memSize,
			0.0,
			0.0,
			0.0,
			finalMemSize,
			0.0,
			newQueue,
			verbose
		);
	}

	/**
	 * Creates a new merge-sorter. The parameters <tt>inputBufferRatio</tt>,
	 * <tt>finalInputBufferRatio</tt>, <tt>firstOutputBufferRatio</tt> and
	 * <tt>outputBufferRatio</tt> are set to <tt>0.0</tt>. That means only one
	 * page is reserved for input- and output-buffer and the maximal fan-in is
	 * used during the intermediate merges and for the final merge. The queue are
	 * given by  {@link xxl.core.collections.queues.ListQueue list-queues}, i.e.,
	 * the intermediate runs are materialized in main memory and they will be
	 * inserted in a {@link xxl.core.collections.queues.DynamicHeap dynamic heap}
	 * and they are compared according their sizes.
	 *
	 * @param input the input iteration to be sorted.
	 * @param comparator the comparator used to compare the elements in the heap
	 *        (replacement-selection).
	 * @param objectSize the size of an object in main memory.
	 * @param memSize the memory available to the merge-sorter during the
	 *        open-phase.
	 * @param finalMemSize the memory available to the merge-sorter during the
	 *        next-phase.
	 * @param verbose if the <tt>verbose</tt> flag set to <tt>true</tt> the
	 *        merge-sorter displays how the memory was distributed internally.
	 *        In addition, the number of merges is displayed.
	 */
	public MergeSorter(
		Iterator input,
		Comparator comparator,
		final int objectSize,
		final int memSize,
		int finalMemSize,
		boolean verbose
	) {
		this(
			input,
			comparator,
			objectSize,
			memSize,
			finalMemSize,
			new Function() {
				public Object invoke(Object function1, Object function2) {
					return new ListQueue();
				}
			},
			verbose
		);
	}

	/**
	 * Creates a new merge-sorter. The parameters <tt>inputBufferRatio</tt>,
	 * <tt>finalInputBufferRatio</tt>, <tt>firstOutputBufferRatio</tt> and
	 * <tt>outputBufferRatio</tt> are set to <tt>0.0</tt>. That means only one
	 * page is reserved for input- and output-buffer and the maximal fan-in is
	 * used during the intermediate merges and for the final merge. The queue are
	 * given by  {@link xxl.core.collections.queues.ListQueue list-queues}, i.e.,
	 * the intermediate runs are materialized in main memory and they will be
	 * inserted in a {@link xxl.core.collections.queues.DynamicHeap dynamic heap}
	 * and they are compared according their sizes.
	 *
	 * @param input the input iteration to be sorted.
	 * @param comparator the comparator used to compare the elements in the heap
	 *        (replacement-selection).
	 * @param objectSize the size of an object in main memory.
	 * @param memSize the memory available to the merge-sorter during the
	 *        open-phase.
	 * @param finalMemSize the memory available to the merge-sorter during the
	 *        next-phase.
	 */
	public MergeSorter(
		Iterator input,
		Comparator comparator,
		final int objectSize,
		final int memSize,
		int finalMemSize
	) {
		this(
			input,
			comparator,
			objectSize,
			memSize,
			finalMemSize,
			false
		);
	}
	
	/**
	 * Opens the merge-sorter, i.e., signals the merge-sorter to reserve
	 * resources, computing and merging the intermediate runs, etc. Before a
	 * merge-sorter has been opened calls to methods like <tt>next</tt> or
	 * <tt>peek</tt> are not guaranteed to yield proper results. Therefore
	 * <tt>open</tt> must be called before a merge-sorter's data can be
	 * processed. Multiple calls to <tt>open</tt> do not have any effect, i.e.,
	 * if <tt>open</tt> was called the merge-sorter remains in the state
	 * <i>opened</i> until its <tt>close</tt> method is called.
	 * 
	 * <p>Note, that a call to the <tt>open</tt> method of a closed cursor
	 * usually does not open it again because of the fact that its state
	 * generally cannot be restored when resources are released respectively
	 * files are closed.</p>
	 */
	public void open() {
		super.open();
		
		// needed for debugging
		Println print = Println.DEFAULT_INSTANCE;
		
		//queue containing the queues to be merged
		Queue queues = (Queue)newQueuesQueue.invoke(
			new Mapper(
				// a grouper is an Iterator of Iterators, the inner Iterators are sorted
				// (sorted with Replacement Selection) runs
				new SortBasedGrouper(new ReplacementSelection(input, heapSize, comparator),
					//Function used by the Grouper to determine the beginning of a new group
					new Predicate() {
						public boolean invoke(Object previous, Object current) {
							return comparator.compare(previous, current) > 0;
						}
					}
				),
				// Function used for Mapper
				// (the only purpose of the Mapper is to map the inner Iterators to queues)
				new Function() {
					public Object invoke(Object sortedIterator) {
						//creates a new queue for the given Iterator
						Queue queue = (Queue)newQueue.invoke(getInputBufferSize(), getOutputBufferSize());
						queue.open();
						Queues.enqueueAll(queue, (Iterator)sortedIterator);
						// the generated queue should not be closed
						// otherwise all inserted elements can be lost
						return new QueueCursor(queue);
					}
				}
			),
			queuesQueueComparator
		);
		runsCreated = true;
		
		// The first fanIn is chosen in a way that the last merge-phase will have to merge exactly fanIn input-queues.
		for (currentFanIn = (queues.size()-finalFanIn+fanIn-2)%(fanIn-1)+2; queues.size()>finalFanIn; currentFanIn = fanIn) {
			//remove currentFanIn queues from queues and merge them to a cursor
			if (verbose)
				print.invoke("merge: fanIn: "+currentFanIn+"  queues.size(): "+queues.size()+" --> "+(queues.size()-currentFanIn+1) );
			cursor = new Merger(
				(Iterator[])Queues.toArray(
					queues,
					new Iterator[currentFanIn]
				),
				comparator
			);
			//create a new queue using the resulting cursor
			Queue queue = (Queue)newQueue.invoke(getInputBufferSize(), getOutputBufferSize());
			queue.open();
			Queues.enqueueAll(queue, cursor);
			// the generated queue should not be closed
			// otherwise all inserted elements can be lost
			// close the merger
			cursor.close();
			queues.enqueue(new QueueCursor(queue));
		}
		//create a Merger for the final merge-phase

		if (verbose)
			print.invoke("final merge: fanIn: "+queues.size());

		this.cursor = new Merger(
			(Iterator[])Queues.toArray(
				queues,
				new Iterator[currentFanIn = queues.size()]
			),
			comparator
		);
		queues.close();
		openPhaseFinished = true;
	}
	
	/**
	 * Closes the merge-sorter, i.e., signals the merge-sorter to clean up
	 * resources, close the input iteration, etc. When a cursor has been closed
	 * calls to methods like <tt>next</tt> or <tt>peek</tt> are not guaranteed
	 * to yield proper results. Multiple calls to <tt>close</tt> do not have any
	 * effect, i.e., if <tt>close</tt> was called the cursor remains in the state
	 * <i>closed</i>.
	 * 
	 * <p>Note, that a closed cursor usually cannot be opened again because of
	 * the fact that its state generally cannot be restored when resources are
	 * released respectively files are closed.</p>
	 */
	public void close() {
		super.close();
		input.close();
		cursor.close();
	}

	/**
	 * Returns <tt>true</tt> if the iteration has more elements. (In other
	 * words, returns <tt>true</tt> if <tt>next</tt> or <tt>peek</tt> would
	 * return an element rather than throwing an exception.)
	 * 
	 * @return <tt>true</tt> if the merge-sorter has more elements.
	 */
	protected boolean hasNextObject() {
		return cursor.hasNext();
	}
	
	/**
	 * Returns the next element in the iteration. This element will be
	 * accessible by some of the merge-sorter's methods, e.g., <tt>update</tt>
	 * or <tt>remove</tt>, until a call to <tt>next</tt> or <tt>peek</tt> occurs.
	 * This is calling <tt>next</tt> or <tt>peek</tt> proceeds the iteration and
	 * therefore its previous element will not be accessible any more.
	 * 
	 * @return the next element in the iteration.
	 */
	protected Object nextObject() {
		return cursor.next();
	}
	
	/**
	 * If the <code>verbose</code>-flag set to <code>true</code>
	 * 		the MergeSorter displays this information
	 * 		how the memory was distributed internally. In addition,
	 * 		the number of merges is displayed.
	 */
	protected void showInfo() {
		System.out.println("-----------------------------------------------------------");
		System.out.println("GLOBAL"                                                     );
		System.out.println("\t\tblockSize:              " + blockSize                   );
		System.out.println("\t\tobjectSize:             " +objectSize                   );
		System.out.println(                                                             );

		System.out.println("OPEN phase"                                                 );
		System.out.println("\t\tmemSize:                " + memSize                     );
		System.out.println(                                                             );
		System.out.println("\trun creation"                                             );
		System.out.println("\t\tfirstOutputBufferSize:  " + firstOutputBufferSize       );
		System.out.println("\t\theapSize:               " + heapSize                    );
		System.out.println(                                                             );

		System.out.println("\tintermediate merges"                                      );
		System.out.println("\t\toutputBufferSize:       " + outputBufferSize            );
		System.out.println("\t\tinputBufferSize:        " + inputBufferSize             );
		System.out.println("\t\tfanIn:                  " + fanIn                       );
		System.out.println(                                                             );

		System.out.println("NEXT phase"                                                 );
		System.out.println("\tfinal merge"                                              );
		System.out.println("\t\tfinalMemSize:           " + finalMemSize                );
		System.out.println("\t\tfinalInputBufferSize:   " + finalInputBufferSize        );
		System.out.println("\t\tfinalFanIn:             " + finalFanIn                  );
		System.out.println("-----------------------------------------------------------");
		System.out.println(                                                             );
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
		
		MergeSorter sorter = new MergeSorter(
			new xxl.core.cursors.sources.RandomIntegers(100000, 300*1000),
			12,
			12*4096,
			0.0,
			0.0,
			0.0,
			4*4096,
			0.0,
			new Function() {
				public Object invoke(Object function1, Object function2) {
					return new ListQueue();
				}
			},
			true
		);
		
		sorter.open();
		
		int count = 0;
		for (Integer old = null; sorter.hasNext(); count++) {
			if (old != null && old.compareTo((Integer) sorter.peek()) > 0)
				throw new RuntimeException("Fehler: Wert " + sorter.peek() + " ist groesser!");
			old = (Integer)sorter.next();
		}
		System.out.println("Objects: " + count);
		
		sorter.close();
	}
}
