import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Becca Erenbaum
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
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		//After writing the tree, you'll need to read the file being compressed one more time. As shown above, the BitInputStream is reset, then read again to compress it
		while(true) {
			int bit = in.readBits(BITS_PER_WORD);
			if (bit==-1) {
				String codex = codings[PSEUDO_EOF];
				out.writeBits(codex.length(), Integer.parseInt(codex,2));
				break;
			}
			String code = codings[bit];
			out.writeBits(code.length(), Integer.parseInt(code,2));
			
		}
		
	}

	private void writeHeader(HuffNode root, BitOutputStream out) {
		//Writing the tree is similar to the code you wrote to read the tree when decompressing.
		HuffNode current = root;
		if(current== null) return;
		if (current.myLeft==null && current.myRight==null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, current.myValue);
			return;
		}
			out.writeBits(1,0);
			writeHeader(root.myLeft,out);
			writeHeader(root.myRight,out);
			
		
		
		
	}

	private String[] makeCodingsFromTree(HuffNode root) {
		//his method returns an array of Strings such that a[val] is the encoding of the 8-bit chunk val
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root,"",encodings);
		return encodings;
	}

	private void codingHelper(HuffNode root, String string, String[] encodings) {
		if (root == null) return;
		if (root.myLeft==null && root.myRight==null) {
			encodings[root.myValue] = string;
			return;
		}
		codingHelper(root.myLeft,string + "0", encodings);
		codingHelper(root.myRight,string + "1", encodings);
		
	}

	private HuffNode makeTreeFromCounts(int[] counts) {
		//You'll use a greedy algorithm and a priority queue of HuffNode objects to create the trie.
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();


		for(int i = 0; i < counts.length; i ++) {
			if (counts[i] > 0) {
		    pq.add(new HuffNode(i,counts[i],null,null));
		}
		}

		while (pq.size() > 1) {
		    HuffNode left = pq.remove();
		    HuffNode right = pq.remove();
		    HuffNode t = new HuffNode(left.myWeight+right.myWeight, left.myWeight+right.myWeight, left, right);
		    // create new HuffNode t with weight from
		    // left.weight+right.weight and left, right subtrees
		    pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}

	private int[] readForCounts(BitInputStream in) {
		//interior tree nodes are indicated by the single bit 0 and leaf nodes are indicated by the single bit 1
		int[] arrayofints = new int[ALPH_SIZE + 1];
		while(true) {
			int y = in.readBits(BITS_PER_WORD);
			if (y== -1) break;
			else {
				arrayofints[y] += 1;
			}
		}
		arrayofints[PSEUDO_EOF] = 1;
		return arrayofints;
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
		//decompress three already-compressed files provided in the assignment
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) throw new HuffException("illegal header starts with " + bits); 
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();
	}

	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		//Once you've read the bit sequence representing tree, you'll read the bits from the BitInputStream representing the compressed file one bit at a time, traversing the tree from the root and going left or right depending on whether you read a zero or a one
		HuffNode current = root; 
		   while (true) {
		       int bits = in.readBits(1);
		       if (bits == -1) {
		           throw new HuffException("bad input, no PSEUDO_EOF");
		       }
		       else { 
		           if (bits == 0) current = current.myLeft;
		      else current = current.myRight;

		           if (current.myLeft==null && current.myRight==null) {
		               if (current.myValue == PSEUDO_EOF) 
		                   break;   // out of loop
		               else {
		                   out.writeBits(BITS_PER_WORD, current.myValue);
		                   current = root; // start back after leaf
		               }
		           }
		       }
		   }

	}

	private HuffNode readTreeHeader(BitInputStream in) {
		//interior tree nodes are indicated by the single bit 0 and leaf nodes are indicated by the single bit 1.
		int bit = in.readBits(1);
		if (bit == -1) throw new HuffException("");
		if (bit == 0) {
		    HuffNode left = readTreeHeader(in);
		    HuffNode right = readTreeHeader(in);
		    return new HuffNode(0,0,left,right);
		}
		else {
		    int value = in.readBits(BITS_PER_WORD + 1);
		    return new HuffNode(value,0,null,null);
		}

		
	}
}