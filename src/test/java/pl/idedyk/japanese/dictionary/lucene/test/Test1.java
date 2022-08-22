package pl.idedyk.japanese.dictionary.lucene.test;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;

import pl.idedyk.japanese.dictionary.api.dictionary.DictionaryManagerAbstract;
import pl.idedyk.japanese.dictionary.api.dictionary.IDatabaseConnector;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.FindWordRequest;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.FindWordResult;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.FindWordResult.ResultItem;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.WordPlaceSearch;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.WordPowerList;
import pl.idedyk.japanese.dictionary.api.dto.DictionaryEntry;
import pl.idedyk.japanese.dictionary.api.dto.DictionaryEntryType;
import pl.idedyk.japanese.dictionary.api.dto.RadicalInfo;
import pl.idedyk.japanese.dictionary.api.dto.TransitiveIntransitivePairWithDictionaryEntry;
import pl.idedyk.japanese.dictionary.api.exception.DictionaryException;
import pl.idedyk.japanese.dictionary.api.keigo.KeigoHelper;
import pl.idedyk.japanese.dictionary.api.tools.KanaHelper;
import pl.idedyk.japanese.dictionary.lucene.LuceneAnalyzer;
import pl.idedyk.japanese.dictionary.lucene.LuceneDatabase;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.JMdict.Entry;

public class Test1 {

	public static void main(String[] args) throws Exception {
		
		// test tokenizera
		LuceneAnalyzer analyzerTest = new LuceneAnalyzer(Version.LUCENE_47, true);
		
		//String text = "1";
		//String text = "1 ala";
		//String text = "Ala ma kota i psa";
		//String text = "Ala ma kota i psa2 i abc";
		//String text = "dziesięć lat";
		//String text = "お早う御座います";
		//String text = "Mały, skromny japoński pomocnik";
		//String text = "sprawiedliwoŚĆ";
		//String text = "スプラッタ・ムービー";
		//String text = "ポーランド";
		//String text = "ﾎﾟｰﾗﾝﾄﾞ";
		//String text = "ﾎｯﾄﾗｲﾝ";
		//String text = "ﾎｯﾄ";
		//String text = "ｿﾌﾄｳｪｱｴﾝｼﾞﾆｱﾘﾝｸﾞ";
		//String text = "ｿﾌﾄｳｪｱ･ｴﾝｼﾞﾆｱﾘﾝｸﾞ";
		//String text = "実効利用者ＩＤ";
		//String text = "遅牛も淀、早牛も淀";
		String text = "マイクロRNA";
		
		TokenStream tokenStream = analyzerTest.tokenStream("a", text);
		
		tokenStream.reset();
		
		while(true) {
			
			boolean incrementTokenResult = tokenStream.incrementToken();
			
			if (incrementTokenResult == false) {
				break;
			}
			
			System.out.println("Token: " + tokenStream.getAttribute(CharTermAttribute.class).toString());			
		}
		
		analyzerTest.close();
						
		System.exit(1);
		
		//
		
		LuceneDatabase luceneDatabase = new LuceneDatabase("/tmp/a/db-lucene");
		
		luceneDatabase.open();
		
		//
		
		DictionaryManagerTest dictionaryManager = new DictionaryManagerTest(luceneDatabase);
		
		//		

		FindWordRequest findWordRequest = new FindWordRequest();
		
		//findWordRequest.searchGrammaFormAndExamples = false;		
		//findWordRequest.word = "shukujitsu";
		
		findWordRequest.word = "kat";
		//findWordRequest.word = text; //"スプラッタ・ムービー";
		//findWordRequest.word = "スプラッタムービー";

		DictionaryEntryType[] dictionaryEntryTypeValues = DictionaryEntryType.values();
		
		List<DictionaryEntryType> dictionaryEntryTypeList = new ArrayList<DictionaryEntryType>();
		
		for (DictionaryEntryType dictionaryEntryType : dictionaryEntryTypeValues) {
			dictionaryEntryTypeList.add(dictionaryEntryType);
		}
		
		//dictionaryEntryTypeList.remove(DictionaryEntryType.WORD_NOUN);
		//..dictionaryEntryTypeList.remove(DictionaryEntryType.WORD_ADJECTIVE_NO);
		
		//findWordRequest.dictionaryEntryTypeList = dictionaryEntryTypeList;
		
		findWordRequest.wordPlaceSearch = WordPlaceSearch.START_WITH;
		
		//findWordRequest.searchRomaji = false;
		
		FindWordResult findWordResult = dictionaryManager.findWord(findWordRequest);
				
		for (ResultItem resultItem : findWordResult.result) {
			
			DictionaryEntry dictionaryEntry = resultItem.getDictionaryEntry();
			
			System.out.println(dictionaryEntry.getId() + " - " + dictionaryEntry.getDictionaryEntryTypeList() + " - " + dictionaryEntry.getKanji() + " - " + 
					dictionaryEntry.getKana() + " - " + dictionaryEntry.getRomaji() + " - " + dictionaryEntry.getTranslates() + " - " +
					dictionaryEntry.getInfo());
			
			System.out.println("--------");
		}
		
		/*
		FindKanjiRequest findKanjiRequest = new FindKanjiRequest();
		
		findKanjiRequest.word = "kot";
		findKanjiRequest.wordPlaceSearch = WordPlaceSearch.ANY_PLACE;
		// findKanjiRequest.strokeCountFrom =
		// findKanjiRequest.strokeCountTo =
		// findKanjiRequest.searchOnlyTop2500 = 
		
		FindKanjiResult findKanjiResult = dictionaryManager.findKanji(findKanjiRequest);
		
		for (KanjiEntry kanjiEntry : findKanjiResult.result) {			
			System.out.println(kanjiEntry.getKanji() + " - " + kanjiEntry.getPolishTranslates() + "- " + kanjiEntry.getInfo());			
		}
				
		//
		*/
		
		////////////////////
		
		//String sentence = "ウイリアムズF1、マッサ後任決定を急がず。最終戦後テストに参加予定のクビカが有利か";
		/*
		String sentence = "【動画】セバスチャン・ブエミ、レッドブルRB8でスイスの峠道を駆ける「記憶に残り続ける」";
		
		TranslateJapaneseSentenceResult translateJapaneseSentenceResult = dictionaryManager.translateJapaneseSentenceTEST(sentence);
		
		List<Token> tokenList = translateJapaneseSentenceResult.getTokenList();
		
		for (Token token : tokenList) {
			
			System.out.println(token.getTokenType());
			System.out.println(token.getToken());
			System.out.println(token.getStartIdx());
			System.out.println(token.getEndIdx());
			
			if (token.getTokenType() == TokenType.FOUND) {
				
				List<ResultItem> resultItemList = token.getFindWordResult().getResult();
				
				for (ResultItem resultItem : resultItemList) {
					
					System.out.println(resultItem.getDictionaryEntry().getTranslates());
					
				}
			}
			
			System.out.println("-------------");
		}
		*/
		
		// dictionary 2
		Entry entry = dictionaryManager.getDictionaryEntry2ById(1232870);
		
		System.out.println(entry.getEntryId());
		
		luceneDatabase.close();
	}
	
	private static class DictionaryManagerTest extends DictionaryManagerAbstract {

		public DictionaryManagerTest(IDatabaseConnector databaseConnector) {
			this.databaseConnector = databaseConnector;
		}
		
		@Override
		public KanaHelper getKanaHelper() {
			return new KanaHelper();
		}

		@Override
		public KeigoHelper getKeigoHelper() {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<TransitiveIntransitivePairWithDictionaryEntry> getTransitiveIntransitivePairsList() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void waitForDatabaseReady() {
			return;
		}

		@Override
		public List<RadicalInfo> getRadicalList() {
			throw new UnsupportedOperationException();
		}

		@Override
		public WordPowerList getWordPowerList() throws DictionaryException {
			throw new UnsupportedOperationException();
		}
	}
}
