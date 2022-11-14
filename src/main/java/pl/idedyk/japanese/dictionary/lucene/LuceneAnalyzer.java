package pl.idedyk.japanese.dictionary.lucene;

import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LetterTokenizer;
import org.apache.lucene.util.Version;

import pl.idedyk.japanese.dictionary.api.dictionary.Utils;

public class LuceneAnalyzer extends Analyzer {

	private final Version matchVersion;
	
	private final boolean removePolishChars;

	public LuceneAnalyzer(Version matchVersion) {
		this(matchVersion, false);
	}

	public LuceneAnalyzer(Version matchVersion, boolean removePolishChars) {
		this.matchVersion = matchVersion;
		
		this.removePolishChars = removePolishChars;
	}
	
	@Override
	protected TokenStreamComponents createComponents(final String fieldName,
			final Reader reader) {
		return new TokenStreamComponents(new Tokenizer(matchVersion, reader));
	}

	private class Tokenizer extends LetterTokenizer {

		public Tokenizer(Version matchVersion, Reader in) {
			super(matchVersion, in);
		}

		public Tokenizer(Version matchVersion, AttributeFactory factory, Reader in) {
			super(matchVersion, factory, in);
		}

		@Override
		protected int normalize(int c) {
						
			// zamiana na znaki
			char[] chars = Character.toChars(c);

			if (chars.length != 1) { // to chyba nigdy nie powinno stac sie				
				return Character.toLowerCase(c);
			}
			
			if (Character.UnicodeBlock.of(c).toString().equals("HALFWIDTH_AND_FULLWIDTH_FORMS") == true) {
				return Character.codePointAt(chars, 0);
			}

			if (Character.UnicodeBlock.of(c).toString().equals("CJK_SYMBOLS_AND_PUNCTUATION") == true) {
				return Character.codePointAt(chars, 0);
			}
									
			// usuwanie polskich znakow
			if (removePolishChars == true) {				
				for (int idx = 0; idx < chars.length; ++idx) {					
					chars[idx] = Utils.removePolishChar(chars[idx]);
				}
			}
						
			// zamiana na male litery
			for (int idx = 0; idx < chars.length; ++idx) {	
				chars[idx] = Character.toLowerCase(chars[idx]);
			}
			
			
			
			// zwrocenie wyniku
			return Character.codePointAt(chars, 0);
		}
		
		@Override
		protected boolean isTokenChar(int c) {
						
			return Character.isLetter(c) || 
					Character.isDigit(c) || 
					(char)c == '・' || 
					(char)c == '･' ||
					(Character.UnicodeBlock.of(c) != null && Character.UnicodeBlock.of(c).toString().equals("CJK_SYMBOLS_AND_PUNCTUATION") == true);
		}
	}
}

