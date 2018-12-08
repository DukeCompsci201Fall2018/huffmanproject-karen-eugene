/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
		out.close();
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int val = in.readBits(BITS_PER_INT);
		// check if compressed file is Huffman encoded
		if (val != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + val);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		// close the output file
		out.close();
	}
	
	/**
	 * Read the bit-sequence representing the tree that is used to decompress/compress
	 * @param in bit-sequence representing the tree
	 * @return HuffNode representing the root of the encoding tree
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		// read 1 bit from header
		int bits = in.readBits(1);
		if(bits == -1) {
			throw new HuffException("illegal header");
		}
		// read internal node
		if(bits == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0, 0, left, right);
		}
		// read leaf node storing 9-bit sequence
		else {
			int value = in.readBits(BITS_PER_WORD + 1); 
			return new HuffNode(value, 0, null, null);
		}
	}
	
	/**
	 * Read bits from the compressed file, traversing root-to-leaf paths
	 * and writing leaf values to the output file
	 * Stop when PSEUDO_EOF is found
	 * @param root
	 * @param in
	 * @param out
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		//out.writeBits(BITS_PER_WORD, val);
		while(true) {
			// read 1 bit from the compressed file
			int bits = in.readBits(1);
			// properly compressed files should never run out of bits
			if(bits == -1){
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			// internal nodes have 0
			else {
				// go to the left subtree if "0" is read and right subtree if "1" is read
				if(bits == 0) current = current.myLeft;
				else current = current.myRight;
				// if current is a leaf node, then either get the character stored in the leaf
				// or stop if the sequence represents PSEUDO_EOF
				if(current.myLeft == null && current.myRight == null) {
					if(current.myValue == PSEUDO_EOF) {
						break;
					}
					else {
						// write 8-bit chunks to file
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root; // start back at the root after leaf
					}
				}
			}
		}
	}
}