package pl.idedyk.japanese.dictionary.lucene.test;

import java.util.ArrayList;
import java.util.Arrays;
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
						
		//System.exit(1);
		
		//
		
		LuceneDatabase luceneDatabase = new LuceneDatabase("db-lucene");
		
		luceneDatabase.open();
		
		//
		
		DictionaryManagerTest dictionaryManager = new DictionaryManagerTest(luceneDatabase);
		
		//		

		/*
		FindWordRequest findWordRequest = new FindWordRequest();
		
		findWordRequest.searchGrammaFormAndExamples = true;		
		//findWordRequest.word = "shukujitsu";
		
		findWordRequest.word = "a";
		// findWordRequest.word = text; //"スプラッタ・ムービー";
		// findWordRequest.word = "スプラッタムービー";
		// findWordRequest.word = "猫";
		// findWordRequest.word = "agaru";
		// findWordRequest.word = "岡村初博";

		DictionaryEntryType[] dictionaryEntryTypeValues = DictionaryEntryType.values(); // Arrays.asList(DictionaryEntryType.WORD_VERB_RU, DictionaryEntryType.WORD_NOUN).toArray(new DictionaryEntryType[] { }); // ;
		
		List<DictionaryEntryType> dictionaryEntryTypeList = new ArrayList<DictionaryEntryType>();
		
		for (DictionaryEntryType dictionaryEntryType : dictionaryEntryTypeValues) {
			dictionaryEntryTypeList.add(dictionaryEntryType);
		}
		
		//dictionaryEntryTypeList.remove(DictionaryEntryType.WORD_NOUN);
		//..dictionaryEntryTypeList.remove(DictionaryEntryType.WORD_ADJECTIVE_NO);
		
		findWordRequest.dictionaryEntryTypeList = dictionaryEntryTypeList;
		
		findWordRequest.wordPlaceSearch = WordPlaceSearch.ANY_PLACE;
		
		findWordRequest.searchTranslate = true;
		findWordRequest.searchInfo = true;
		findWordRequest.searchRomaji = true;
		findWordRequest.searchOnlyCommonWord = false;
		findWordRequest.searchName = true;
		
		FindWordResult findWordResult = dictionaryManager.findWord(findWordRequest);
				
		for (ResultItem resultItem : findWordResult.result) {
			
			Entry entry = resultItem.getEntry();
			
			if (entry != null) {
				System.out.println("Entry");
				System.out.println(entry.getEntryId());
				System.out.println(resultItem.getKanjiList());
				System.out.println(resultItem.getKanaList());
				System.out.println(resultItem.getRomajiList());
				System.out.println(resultItem.getTranslates());
			}
			
			DictionaryEntry oldDictionaryEntry = resultItem.getOldDictionaryEntry();
			
			if (oldDictionaryEntry != null) {
				System.out.println("oldDictionaryEntry");
				System.out.println(oldDictionaryEntry.getId() + " - " + oldDictionaryEntry.getDictionaryEntryTypeList() + " - " + oldDictionaryEntry.getKanji() + " - " + 
						oldDictionaryEntry.getKana() + " - " + oldDictionaryEntry.getRomaji() + " - " + oldDictionaryEntry.getTranslates() + " - " +
						oldDictionaryEntry.getInfo());				
			}
						
			System.out.println("--------");
		}
		*/
		
		/*
		FindKanjiRequest findKanjiRequest = new FindKanjiRequest();
		
		findKanjiRequest.word = "kot";
		findKanjiRequest.wordPlaceSearch = WordPlaceSearch.ANY_PLACE;
		// findKanjiRequest.strokeCountFrom =
		// findKanjiRequest.strokeCountTo =
		// findKanjiRequest.searchOnlyTop2500 = 
		
		FindKanjiResult findKanjiResult = dictionaryManager.findKanji(findKanjiRequest);
		
		for (KanjiCharacterInfo kanjiCharacterInfo : findKanjiResult.result) {			
			System.out.println(kanjiCharacterInfo.getKanji() + " - " + Utils.getPolishTranslates(kanjiCharacterInfo) + "- " + Utils.getPolishAdditionalInfo(kanjiCharacterInfo));			
		}
		*/
				
		//
		
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
		
		/*
		// dictionary 2
		Entry entry = dictionaryManager.getDictionaryEntry2ById(1232870);
		
		System.out.println(entry.getEntryId());
		*/
		
		/*
		KanjiCharacterInfo kanjiEntry = dictionaryManager.getKanjiEntryById(15);
		
		System.out.println(kanjiEntry.getKanji());
		System.out.println(kanjiEntry.getMisc2().getStrokePaths());
		*/
		
		// int dictionaryEntriesSize = dictionaryManager.getDictionaryEntriesSize();
		
		// System.out.println("dictionaryEntriesSize: " + dictionaryEntriesSize);
		
		Entry entry = dictionaryManager.getDictionaryEntry2ByOldPolishJapaneseDictionaryId(99296);
		
		System.out.println("AAAAA: " + entry.getEntryId());
		
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
