import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

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

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}
	
	/**
	 * @param in bit-sequence representing the tree
	 * @return integer array containing frequencies of each 8-bit character/chunk in the file being compressed
	 */
	private int[] readForCounts(BitInputStream in) {
		//Create the array of size 257
		int[] freq = new int[ALPH_SIZE + 1];
		//Set to indicate one occurrence of PSEUDO_EOS
		freq[PSEUDO_EOF] = 1;
		
		while(true) {
			//Read 8 bit chunks while there is still input to read
			int bits = in.readBits(BITS_PER_WORD);
			if(bits == -1) break;
			freq[bits]++;
		}
		return freq;
	}
	
	/**
	 * Makes tree from frequencies of bits
	 * @param integer array containing bit frequencies
	 * @return HuffNode representing the root of the encoding tree
	 */
	private HuffNode makeTreeFromCounts(int[] freq) {
		
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for(int index = 0; index < freq.length; index++) {
			// only add nodes to the pq for 8-bit values that occur (i.e. have freq >= 1)
			if(freq[index] > 0) {
				pq.add(new HuffNode(index,freq[index],null,null));
			}
		}

		while (pq.size() > 1) {
		    HuffNode left = pq.remove();
		    HuffNode right = pq.remove();
		    // create new HuffNode t with weight from
		    // left.weight+right.weight and left, right subtrees
		    HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);   
		    pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	/**
	 * Makes compressed code from Huffman Tree
	 * @param HuffNode representing root of Huffman Tree
	 * @return String array representing compressed pathways
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		Map<Integer, String> out = new TreeMap<>();
		codingHelper(root, out, "");
		return out.values().toArray(new String[0]);
	}
	
	private void codingHelper(HuffNode node, Map<Integer, String> out, String visited) {
		if (node == null) {
			return;
		}

		boolean leftExists = node.myLeft != null;
		boolean rightExists = node.myRight != null;

		if (!leftExists && !rightExists) {
			out.put(node.myValue, visited);
		}

		codingHelper(node.myLeft, out, visited + "0");
		codingHelper(node.myRight, out, visited + "1");
	}
	
	/**
	 * Writes out the tree
	 * @param HuffNode representing root of Huffman Tree, out bit-sequence representing the tree
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
				HuffNode left = root.myLeft;
				HuffNode right = root.myRight;
				// if node is a leaf, write 1 bit of 1 and the 9-bit sequence stored in the node
				if(left == null && right == null) {
					out.writeBits(1, 1);
					out.writeBits(BITS_PER_WORD + 1, root.myValue);
				}
				// write 1 bit of 0 and make two recursive calls if node is internal
				else {
					out.writeBits(1, 0);
					writeHeader(left, out);
					writeHeader(right, out);
				}
	}
	
	private void writeCompressedBits(String[] encodings, BitInputStream in, BitOutputStream out) {
		while(true) {
			int bits = in.readBits(BITS_PER_WORD);
			if(bits == -1) break;
			String encoding = encodings[bits];
			out.writeBits(encoding.length(), Integer.parseInt(encoding, 2));
		}
		String encoding = encodings[PSEUDO_EOF];
		out.writeBits(encoding.length(), Integer.parseInt(encoding, 2));
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