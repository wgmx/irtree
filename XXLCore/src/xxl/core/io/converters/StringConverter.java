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

package xxl.core.io.converters;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * This class provides a converter that is able to read and write
 * <tt>String</tt> objects. In addition to the read and write methods that
 * read or write <tt>String</tt> objects (these methods get
 * <tt>Object</tt>s and cast them to <tt>String</tt> objects) this class
 * contains readUTF and writeUTF methods that gets directly
 * <tt>String</tt> objects.<p>
 *
 * Example usage (1).
 * <pre>
 *     // catch IOExceptions
 *
 *     try {
 *
 *         // create a byte array output stream
 *
 *         ByteArrayOutputStream output = new ByteArrayOutputStream();
 *
 *         // write two strings to the output stream
 *
 *         StringConverter.DEFAULT_INSTANCE.write(new DataOutputStream(output), "Hello world!");
 *         StringConverter.DEFAULT_INSTANCE.write(new DataOutputStream(output), "That's all, folks!");
 *
 *         // create a byte array input stream on the output stream
 *
 *         ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
 *
 *         // read two strings from the input stream
 *
 *         String s1 = (String)StringConverter.DEFAULT_INSTANCE.read(new DataInputStream(input));
 *         String s2 = (String)StringConverter.DEFAULT_INSTANCE.read(new DataInputStream(input));
 *
 *         // print the value and the object
 *
 *         System.out.println(s1);
 *         System.out.println(s2);
 *
 *         // close the streams after use
 *
 *         input.close();
 *         output.close();
 *     }
 *     catch (IOException ioe) {
 *         System.out.println("An I/O error occured.");
 *     }
 * </pre>
 *
 * @see DataInput
 * @see DataOutput
 * @see IOException
 */
public class StringConverter extends Converter {

	/**
	 * This instance can be used for getting a default instance of
	 * StringConverter. It is similar to the <i>Singleton Design
	 * Pattern</i> (for further details see Creational Patterns, Prototype
	 * in <i>Design Patterns: Elements of Reusable Object-Oriented
	 * Software</i> by Erich Gamma, Richard Helm, Ralph Johnson, and John
	 * Vlissides) except that there are no mechanisms to avoid the
	 * creation of other instances of StringConverter.
	 */
	public static final StringConverter DEFAULT_INSTANCE = new StringConverter();

	/**
	 * Sole constructor. (For invocation by subclass constructors,
	 * typically implicit.)
	 */
	public StringConverter() {
	}

	/**
	 * Reads in a string that has been encoded using a modified UTF-8
	 * format and returns the restored (<tt>String</tt>) object. <br>
	 * This implementation ignores the specified object and returns a new
	 * <tt>String</tt> object by calling the readUTF method of the
	 * specified data input. So it does not matter when the specified
	 * object is <tt>null</tt>.
	 *
	 * @param dataInput the stream to read a modified UTF-8 representation
	 *        of a string from in order to return a <tt>String</tt>
	 *        object.
	 * @param object the (<tt>String</tt>) object to be restored. In this
	 *        implementation it is ignored.
	 * @return the read <tt>String</tt> object.
	 * @throws IOException if I/O errors occur.
	 */
	public Object read (DataInput dataInput, Object object) throws IOException{
		return dataInput.readUTF();
	}

	/**
	 * Reads in a string that has been encoded using a modified UTF-8
	 * format and returns the restored (<tt>String</tt>) object. <br>
	 * This implementation calls the read method and casts its result to
	 * <tt>String</tt>.
	 *
	 * @param dataInput the stream to read a modified UTF-8 representation
	 *        of a string from in order to return a <tt>String</tt>
	 *        object.
	 * @return the read <tt>String</tt> object.
	 * @throws IOException if I/O errors occur.
	 */
	public String readUTF (DataInput dataInput) throws IOException {
		return (String)read(dataInput);
	}

	/**
	 * Writes the specified <tt>String</tt> object to the specified data
	 * output using a modified UTF-8 format. <br>
	 * This implementation uses the writeUTF method of the specified data
	 * output in order to write the specified object.
	 *
	 * @param dataOutput the stream to write the modified UTF-8
	 *        representation of the specified <tt>String</tt> object to.
	 * @param object the <tt>String</tt> object that modified UTF-8
	 *        representation should be written to the data output.
	 * @throws IOException includes any I/O exceptions that may occur.
	 */
	public void write (DataOutput dataOutput, Object object) throws IOException {
		dataOutput.writeUTF((String)object);
	}

	/**
	 * Writes the specified <tt>String</tt> object to the specified data
	 * output using a modified UTF-8 format. <br>
	 * This implementation uses the write method output in order to write
	 * the specified object.
	 *
	 * @param dataOutput the stream to write the modified UTF-8
	 *        representation of the specified <tt>String</tt> object to.
	 * @param s the <tt>String</tt> object that modified UTF-8
	 *        representation should be written to the data output.
	 * @throws IOException includes any I/O exceptions that may occur.
	 */
	public void writeUTF (DataOutput dataOutput, String s) throws IOException {
		write(dataOutput, s);
	}

	/**
	 * The main method contains some examples how to use a
	 * StringConverter. It can also be used to test the functionality of a
	 * StringConverter.
	 *
	 * @param args array of <tt>String</tt> arguments. It can be used to
	 *        submit parameters when the main method is called.
	 */
	public static void main (String [] args) {

		//////////////////////////////////////////////////////////////////
		//                      Usage example (1).                      //
		//////////////////////////////////////////////////////////////////

		// catch IOExceptions
		try {
			// create a byte array output stream
			java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
			// write two strings to the output stream
			StringConverter.DEFAULT_INSTANCE.write(new java.io.DataOutputStream(output), "Hello world!");
			StringConverter.DEFAULT_INSTANCE.write(new java.io.DataOutputStream(output), "That's all, folks!");
			// create a byte array input stream on the output stream
			java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(output.toByteArray());
			// read two strings from the input stream
			String s1 = (String)StringConverter.DEFAULT_INSTANCE.read(new java.io.DataInputStream(input));
			String s2 = (String)StringConverter.DEFAULT_INSTANCE.read(new java.io.DataInputStream(input));
			// print the value and the object
			System.out.println(s1);
			System.out.println(s2);
			// close the streams after use
			input.close();
			output.close();
		}
		catch (IOException ioe) {
			System.out.println("An I/O error occured.");
		}
		System.out.println();
	}
}