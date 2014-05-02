/*
    Reed Solomon Encoding and Decoding for Nxt
    
    Version: 1.0, license: Public Domain, coder: NxtChg (admin@nxtchg.com)
    Java Version: ChuckOne (ChuckOne@mail.de).
*/
package nxt.crypto;



final class ReedSolomon {

	public static void main(String [] args) throws Exception
	{
		Object[][] test_accounts = {
			{8264278205416377583L, "K59H-9RMF-64CY-9X6E7"},
			{8301188658053077183L, "4Q7Z-5BEE-F5JZ-9ZXE8"},
			{1798923958688893959L, "GM29-TWRT-M5CK-3HSXK"},
			{6899983965971136120L, "MHMS-VHZT-W5CY-7CFJZ"},
			{1629938923029941274L, "JM2U-U4AE-G7WF-3NP9F"},
			{6474206656034063375L, "4K2H-NVHQ-7WXY-72AQM"},
			{1691406066100673814L, "Y9AQ-VE8F-U9SY-3NAYG"},
			{2992669254877342352L, "6UNJ-UMFM-Z525-4S24M"},
			{43918951749449909L, "XY7P-3R8Y-26FC-2A293"},
			{9129355674909631300L, "YSU6-MRRL-NSC4-9WHEX"}
		};
		for (Object[] test_account : test_accounts)
		{
			if (!ReedSolomon.encode((Long) test_account[0]).equals(test_account[1]))
			{
				System.out.println("ERROR: " + ReedSolomon.encode((Long) test_account[0]) + " != " + test_account[1]);
			}

			if (!ReedSolomon.decode((String) test_account[1]).equals(test_account[0]))
			{
				System.out.println("ERROR: " + ReedSolomon.decode((String) test_account[1]) + " != " + test_account[0]);
			}
            System.out.println("Success: " + test_account[0] + " == " + test_account[1]);
		}
	}

	public static final int[] initial_codeword = {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
	public static final int[] gexp = {1, 2, 4, 8, 16, 5, 10, 20, 13, 26, 17, 7, 14, 28, 29, 31, 27, 19, 3, 6, 12, 24, 21, 15, 30, 25, 23, 11, 22, 9, 18, 1};
	public static final int[] glog = {0, 0, 1, 18, 2, 5, 19, 11, 3, 29, 6, 27, 20, 8, 12, 23, 4, 10, 30, 17, 7, 22, 28, 26, 21, 25, 9, 16, 13, 14, 24, 15};
	public static final int[] codeword_map = {3, 2, 1, 0, 7, 6, 5, 4, 13, 14, 15, 16, 12, 8, 9, 10, 11};
	public static final String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
	
	public static final int base_32_length = 13;
	public static final int base_10_length = 20;
	public static final int codeword_length = ReedSolomon.initial_codeword.length;
	public static final int alphabet_length = ReedSolomon.alphabet.length();
	
	public static String encode(Long plain)
	{
		String plain_string = Long.toString(plain);
		int length = plain_string.length();

		int[] plain_string_10 = new int[ReedSolomon.base_10_length];
		for(int index = 0; index < length; index += 1)
		{
			plain_string_10[index] = (int)plain_string.charAt(index) - (int)'0';
		}
	
		int codeword_length = 0;
		int[] codeword = new int[ReedSolomon.codeword_length];
		do // base 10 to base 32 conversion
		{
			int new_length = 0;
			int digit_32 = 0;
			for(int index = 0; index < length; index += 1)
			{
				digit_32 = digit_32 * 10 + plain_string_10[index];
				if(digit_32 >= 32)
				{
					plain_string_10[new_length] = digit_32 >> 5;
					digit_32 &= 31;
					new_length += 1;
				}
				else if(new_length > 0)
				{
					plain_string_10[new_length] = 0;
					new_length += 1;
				}
			}
			length = new_length;

			codeword[codeword_length] = digit_32;
			codeword_length += 1;
		}
		while(length > 0);

		int[] p = {0, 0, 0, 0};
		for(int index = ReedSolomon.base_32_length - 1; index >= 0; index -= 1)
		{
			final int fb = codeword[index] ^ p[3];
			p[3] = p[2] ^ ReedSolomon.gmult(30, fb);
			p[2] = p[1] ^ ReedSolomon.gmult(6, fb);
			p[1] = p[0] ^ ReedSolomon.gmult(9, fb);
			p[0] =        ReedSolomon.gmult(17, fb);
		}
		System.arraycopy(p, 0, codeword, ReedSolomon.base_32_length, ReedSolomon.codeword_length - ReedSolomon.base_32_length);
		
		StringBuilder cypher_string_builder = new StringBuilder();
		for(int i = 0; i < 17; i++)
		{
			final int codework_index = ReedSolomon.codeword_map[i];
			final int alphabet_index = codeword[codework_index];
			cypher_string_builder.append(ReedSolomon.alphabet.charAt(alphabet_index));
	
			if((i & 3) == 3 && i < 13)
			{
				cypher_string_builder.append("-");
			}
		}
		return cypher_string_builder.toString();
	}
	
	public static Long decode(String cypher_string) throws DecodeException
	{
		int[] codeword = new int[ReedSolomon.codeword_length];
		System.arraycopy(ReedSolomon.initial_codeword, 0, codeword, 0, ReedSolomon.codeword_length);
	
		int codeword_length = 0;
		final int cypher_string_length = cypher_string.length();
		for(int index = 0; index < cypher_string_length; index++)
		{
			int position_in_alphabet = ReedSolomon.alphabet.indexOf(cypher_string.charAt(index));
			
			if(position_in_alphabet <= -1 || position_in_alphabet > ReedSolomon.alphabet_length)
			{
				continue;
			}

			if(codeword_length > 16)
			{
				throw new CodewordTooLongException();
			}

			int codework_index = ReedSolomon.codeword_map[codeword_length];
			codeword[codework_index] = position_in_alphabet;
			codeword_length += 1;
		}

		if(codeword_length == 17 && !ReedSolomon.is_codeword_valid(codeword) || codeword_length != 17)
		{
			throw new CodewordInvalidException();
		}
		
		int length = ReedSolomon.base_32_length;
		int[] cypher_string_32 = new int[length];
		for(int i = 0; i < length; i++)
		{
			cypher_string_32[i] = codeword[length - i - 1];
		}
	
		StringBuilder plain_string_builder = new StringBuilder();
		do // base 32 to base 10 conversion
		{
			int new_length = 0;
			int digit_10 = 0;
	
			for(int index = 0; index < length; index++)
			{
				digit_10 = digit_10 * 32 + cypher_string_32[index];
	
				if(digit_10 >= 10)
				{
					cypher_string_32[new_length] = digit_10 / 10;
					digit_10 %= 10;
					new_length += 1;
				}
				else if(new_length > 0)
				{
					cypher_string_32[new_length] = 0;
					new_length += 1;
				}
			}

			length = new_length;

			plain_string_builder.appendCodePoint(digit_10 + (int)'0');
		}
		while(length > 0);
	
		return Long.parseLong(plain_string_builder.reverse().toString());
	}
	
	public static Long decodeOrNull(String cypher_string)
	{
		try
		{
			return ReedSolomon.decode(cypher_string);
		}
		catch (DecodeException exception)
		{
			return null;
		}
	}
	
	private static int gmult(int a, int b)
	{
		if(a == 0 || b == 0)
		{
			return 0;
		}
	
		int idx = (ReedSolomon.glog[a] + ReedSolomon.glog[b]) % 31;
	
		return ReedSolomon.gexp[idx];
	}
	
	private static boolean is_codeword_valid(int[] codeword)
	{
		int sum = 0;
		
		for(int i = 1; i < 5; i++)
		{
			int t = 0;
			
			for(int j = 0; j < 31; j++)
			{
	            if(j > 12 && j < 27)
            	{
	            	continue;
            	}
	
				int pos = j;
				if(j > 26)
				{
					pos -= 14;
				}
	
				t ^= ReedSolomon.gmult(codeword[pos], ReedSolomon.gexp[(i * j) % 31]);
			}
	
			sum |= t;
		}
	
		return sum == 0;
	}

    public abstract static class DecodeException extends Exception {
    }

    public static final class CodewordTooLongException extends DecodeException {
    }

    public static final class CodewordInvalidException extends DecodeException {
    }

}

