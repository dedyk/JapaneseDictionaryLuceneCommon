package pl.idedyk.japanese.dictionary.lucene;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.spans.SpanFirstQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spell.JaroWinklerDistance;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import com.google.gson.Gson;

import pl.idedyk.japanese.dictionary.api.dictionary.IDatabaseConnector;
import pl.idedyk.japanese.dictionary.api.dictionary.Utils;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.FindKanjiRequest;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.FindKanjiResult;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.FindWordRequest;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.WordPlaceSearch;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.FindWordResult;
import pl.idedyk.japanese.dictionary.api.dto.AttributeType;
import pl.idedyk.japanese.dictionary.api.dto.DictionaryEntry;
import pl.idedyk.japanese.dictionary.api.dto.DictionaryEntryType;
import pl.idedyk.japanese.dictionary.api.dto.GroupEnum;
import pl.idedyk.japanese.dictionary.api.dto.GroupWithTatoebaSentenceList;
import pl.idedyk.japanese.dictionary.api.dto.TatoebaSentence;
import pl.idedyk.japanese.dictionary.api.exception.DictionaryException;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.JMdict;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.JMdict.Entry;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.MiscInfo;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.OldPolishJapaneseDictionaryInfo;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.OldPolishJapaneseDictionaryInfoAttributeListInfo;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.OldPolishJapaneseDictionaryInfoEntriesInfo;
import pl.idedyk.japanese.dictionary2.kanjidic2.xsd.KanjiCharacterInfo;
import pl.idedyk.japanese.dictionary2.kanjidic2.xsd.Misc2Info;

public class LuceneDatabase implements IDatabaseConnector {

	private String dbDir;
	
	private Directory index;
	private LuceneAnalyzer analyzer;
	private LuceneAnalyzer analyzerWithoutPolishChars;
	private IndexReader reader;
	private IndexSearcher searcher;

	private ConcurrentMap<LuceneDatabaseSuggesterAndSpellCheckerSource, Lookup> lookupSuggesterMap;
	private ConcurrentMap<LuceneDatabaseSuggesterAndSpellCheckerSource, SpellCheckerIndex> spellCheckerMap;
	
	private static final int MAX_DICTIONARY_RESULT = 50;
	
	private static final int MAX_KANJI_RESULT = 100;
	private static final int MAX_KANJI_STROKE_COUNT_RESULT = 2000;
	
	public LuceneDatabase(String dbDir) {
		this.dbDir = dbDir;
	}

	public void open() throws IOException {

		index = FSDirectory.open(new File(dbDir));
		//analyzer = new LuceneAnalyzer(Version.LUCENE_47);
		analyzerWithoutPolishChars = new LuceneAnalyzer(Version.LUCENE_47, true);
		reader = DirectoryReader.open(index);
		searcher = new IndexSearcher(reader);
	}
	
	public void openSuggester() throws IOException {
		
		lookupSuggesterMap = new ConcurrentHashMap<LuceneDatabaseSuggesterAndSpellCheckerSource, Lookup>();
		
		initializeSuggester(LuceneDatabaseSuggesterAndSpellCheckerSource.DICTIONARY_ENTRY_WEB);
		initializeSuggester(LuceneDatabaseSuggesterAndSpellCheckerSource.DICTIONARY_ENTRY_ANDROID);

		initializeSuggester(LuceneDatabaseSuggesterAndSpellCheckerSource.KANJI_ENTRY_WEB);
		initializeSuggester(LuceneDatabaseSuggesterAndSpellCheckerSource.KANJI_ENTRY_ANDROID);
	}
	
	private void initializeSuggester(LuceneDatabaseSuggesterAndSpellCheckerSource source) throws IOException {
		
		// !!! UWAGA: Podobna metoda jest w klasie LuceneDBGenerator.cacheSuggester !!!
		
		LuceneDictionary luceneDictionary = new LuceneDictionary(reader, source.getSuggestionListFieldName());		
		Lookup lookup = new AnalyzingSuggester(analyzerWithoutPolishChars);
		
		// proba zaladowania z cache'u
		File cacheFile = new File(dbDir, source.getSuggesterCacheFileName());
		
		if (cacheFile.isFile() == true) { // cache istnieje
			lookup.load(new FileInputStream(cacheFile));
			
		} else { // wygenerowanie zawartosci
			lookup.build(luceneDictionary);
		}
		
		//
		
		lookupSuggesterMap.put(source, lookup);
	}
	
	public void openSpellChecker() throws IOException {
		
		spellCheckerMap = new ConcurrentHashMap<LuceneDatabaseSuggesterAndSpellCheckerSource, SpellCheckerIndex>();
		
		initializeSpellChecker(LuceneDatabaseSuggesterAndSpellCheckerSource.DICTIONARY_ENTRY_WEB);
		initializeSpellChecker(LuceneDatabaseSuggesterAndSpellCheckerSource.DICTIONARY_ENTRY_ANDROID);

		initializeSpellChecker(LuceneDatabaseSuggesterAndSpellCheckerSource.KANJI_ENTRY_WEB);
		initializeSpellChecker(LuceneDatabaseSuggesterAndSpellCheckerSource.KANJI_ENTRY_ANDROID);
	}
	
	private void initializeSpellChecker(LuceneDatabaseSuggesterAndSpellCheckerSource source) throws IOException {
		
		// !!! UWAGA: Podobna metoda jest w klasie LuceneDBGenerator.initializeSpellChecker !!!
		
		// otwieranie podindeksu
		File subDbOutDirFile = new File(dbDir, "subindex_" + source.getSpellCheckerListFieldName());

		Directory subIndex = FSDirectory.open(subDbOutDirFile);
		
		//
		
		LuceneDictionary luceneDictionary = new LuceneDictionary(reader, source.getSpellCheckerListFieldName());
		
		@SuppressWarnings("resource")
		SpellChecker spellChecker = new SpellChecker(subIndex, new JaroWinklerDistance());
				
		IndexWriterConfig indexWriterConfig = new IndexWriterConfig(Version.LUCENE_47, analyzer);
		
		spellChecker.indexDictionary(luceneDictionary, indexWriterConfig, false);

		//
		
		SpellCheckerIndex spellCheckerIndex = new SpellCheckerIndex();
		
		spellCheckerIndex.spellChecker = spellChecker;
		spellCheckerIndex.index = subIndex;		
		
		spellCheckerMap.put(source, spellCheckerIndex);
	}	
	
	public void close() throws IOException {

		if (reader != null) {
			reader.close();
		}

		if (index != null) {
			index.close();
		}
		
		if (spellCheckerMap != null) {
			
			Collection<SpellCheckerIndex> spellCheckerMapValues = spellCheckerMap.values();
			
			for (SpellCheckerIndex spellCheckerIndex : spellCheckerMapValues) {
				
				spellCheckerIndex.spellChecker.close();
				
				spellCheckerIndex.index.close();				
			}			
		}
	}

	@Override
	public FindWordResult findDictionaryEntries(FindWordRequest findWordRequest) throws DictionaryException {
		
		FindWordResult findWordResult = new FindWordResult();
		findWordResult.result = new ArrayList<FindWordResult.ResultItem>();
		
		Gson gson = new Gson();
		
		if (findWordRequest.searchMainDictionary == false) {
			return findWordResult;
		}

		final int maxResult = MAX_DICTIONARY_RESULT;
		                
		String[] wordSplited = getTokenizedWords(analyzerWithoutPolishChars, findWordRequest.word);
		String rejoinedWord = createRejoinedWord(wordSplited);
		
		try {
			//if (findWordRequest.wordPlaceSearch != WordPlaceSearch.ANY_PLACE) {

			BooleanQuery query = new BooleanQuery();

			// object type
			PhraseQuery objectTypeQuery = new PhraseQuery();
			objectTypeQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.dictionaryEntry2_objectType));

			query.add(objectTypeQuery, Occur.MUST);

			// common word
			// FM_FIXME: do naprawy/zaimplementowania -> sprawdzenia
			if (findWordRequest.searchOnlyCommonWord == true) {	
				PhraseQuery onlyCommonWordQuery = new PhraseQuery();
				
				onlyCommonWordQuery.add(new Term(LuceneStatic.dictionaryEntry2_attributeList, AttributeType.COMMON_WORD.toString()));
				
				query.add(onlyCommonWordQuery, Occur.MUST);
				
			} else {				
				PhraseQuery onlyCommonWordQuery = new PhraseQuery();
				
				onlyCommonWordQuery.add(new Term(LuceneStatic.dictionaryEntry2_attributeList, AttributeType.COMMON_WORD.toString()));
								
				query.add(onlyCommonWordQuery, Occur.SHOULD);
			}
			
			BooleanQuery wordBooleanQuery = new BooleanQuery();

			if (findWordRequest.searchKanji == true) {
				wordBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.dictionaryEntry2_kanji, findWordRequest.wordPlaceSearch, false), Occur.SHOULD);				
			}

			if (findWordRequest.searchKana == true) {
				wordBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.dictionaryEntry2_kana, findWordRequest.wordPlaceSearch, false), Occur.SHOULD);				
			}

			if (findWordRequest.searchRomaji == true) {
				wordBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.dictionaryEntry2_romaji, findWordRequest.wordPlaceSearch, true), Occur.SHOULD);
			}

			if (findWordRequest.searchTranslate == true) {
				//wordBooleanQuery.add(createQuery(wordSplitedToLowerCase, LuceneStatic.dictionaryEntry_translatesList, findWordRequest.wordPlaceSearch), Occur.SHOULD);

				wordBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.dictionaryEntry2_translatesList, findWordRequest.wordPlaceSearch, true), Occur.SHOULD);
			}

			if (findWordRequest.searchInfo == true) {
				//wordBooleanQuery.add(createQuery(wordSplitedToLowerCase, LuceneStatic.dictionaryEntry_info, findWordRequest.wordPlaceSearch), Occur.SHOULD);

				wordBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.dictionaryEntry2_info, findWordRequest.wordPlaceSearch, true), Occur.SHOULD);
			}

			query.add(wordBooleanQuery, Occur.MUST);

			// FM_FIXME: do naprawy zaimplementowania
			/*
			BooleanQuery dictionaryEntryTypeListFilter = createDictionaryEntryTypeListFilter(LuceneStatic.dictionaryEntry2_dictionaryEntryTypeList, findWordRequest.dictionaryEntryTypeList);
			
			if (dictionaryEntryTypeListFilter != null) {
				query.add(dictionaryEntryTypeListFilter, Occur.MUST);
			}
			*/			
			
			ScoreDoc[] scoreDocs = searcher.search(query, null, maxResult + 1).scoreDocs;

			for (ScoreDoc scoreDoc : scoreDocs) {

				Document foundDocument = searcher.doc(scoreDoc.doc);

				// String idString = foundDocument.get(LuceneStatic.dictionaryEntry2_id);
				String entryBody = foundDocument.get(LuceneStatic.dictionaryEntry2_entry);
				
				JMdict.Entry entry = gson.fromJson(entryBody, JMdict.Entry.class);

				findWordResult.result.add(new FindWordResult.ResultItem(entry, false, false));
			}

/*
			} else { // findWordRequest.wordPlaceSearch == WordPlaceSearch.ANY_PLACE

				for (int docId = 0; docId < reader.maxDoc(); docId++) {

					Document document = reader.document(docId);

					String objectType = document.get(LuceneStatic.objectType);

					if (objectType.equals(LuceneStatic.dictionaryEntry_objectType) == false) {
						continue;
					}

					boolean addDictionaryEntry = false;

					String idString = document.get(LuceneStatic.dictionaryEntry_id);

					List<String> dictionaryEntryTypeList = Arrays.asList(document.getValues(LuceneStatic.dictionaryEntry_dictionaryEntryTypeList));
					List<String> attributeList = Arrays.asList(document.getValues(LuceneStatic.dictionaryEntry_attributeList));
					List<String> groupsList = Arrays.asList(document.getValues(LuceneStatic.dictionaryEntry_groupsList));

					String prefixKanaString = document.get(LuceneStatic.dictionaryEntry_prefixKana);

					String kanjiString = document.get(LuceneStatic.dictionaryEntry_kanji);
					List<String> kanaList = Arrays.asList(document.getValues(LuceneStatic.dictionaryEntry_kanaList));

					String prefixRomajiString = document.get(LuceneStatic.dictionaryEntry_prefixRomaji);

					List<String> romajiList = Arrays.asList(document.getValues(LuceneStatic.dictionaryEntry_romajiList));				
					List<String> translateList = Arrays.asList(document.getValues(LuceneStatic.dictionaryEntry_translatesList));

					String infoString = document.get(LuceneStatic.dictionaryEntry_info);

					if (findWordRequest.wordPlaceSearch == WordPlaceSearch.ANY_PLACE) {
						
						boolean goodDictionaryEntryType = false;
						
						if (findWordRequest.dictionaryEntryTypeList == null || findWordRequest.dictionaryEntryTypeList.size() == 0) {
							goodDictionaryEntryType = true;
							
						} else {
							
							List<String> findWordRequestDictionaryEntryTypeStringList = DictionaryEntryType.convertToValues(findWordRequest.dictionaryEntryTypeList);
							
							for (String currentFoundDictionaryEntryType : dictionaryEntryTypeList) {
								
								if (findWordRequestDictionaryEntryTypeStringList.contains(currentFoundDictionaryEntryType) == true) {
									goodDictionaryEntryType = true;
									
									break;
								}								
							}
						}
						
						if (goodDictionaryEntryType == true) {

							if (findWordRequest.searchKanji == true) {

								if (kanjiString.indexOf(findWordRequest.word) != -1) {
									addDictionaryEntry = true;
								}
							}

							if (addDictionaryEntry == false && findWordRequest.searchKana == true) {

								for (String currentKana : kanaList) {							
									if (currentKana.indexOf(findWordRequest.word) != -1) {
										addDictionaryEntry = true;

										break;
									}							
								}						
							}

							if (addDictionaryEntry == false && findWordRequest.searchRomaji == true) {

								for (String currentRomaji : romajiList) {
									if (currentRomaji.indexOf(findWordRequest.word) != -1) {
										addDictionaryEntry = true;

										break;
									}							
								}
							}

							if (addDictionaryEntry == false && findWordRequest.searchTranslate == true) {

								for (String currentTranslate : translateList) {

									if (currentTranslate.toLowerCase(Locale.getDefault()).indexOf(wordToLowerCase) != -1) {
										addDictionaryEntry = true;

										break;
									}							
								}						

								List<String> translateListWithoutPolishChars = Arrays.asList(document.getValues(LuceneStatic.dictionaryEntry_translatesListWithoutPolishChars));

								if (translateListWithoutPolishChars != null && translateListWithoutPolishChars.size() > 0) {

									for (String currentTranslateListWithoutPolishChars : translateListWithoutPolishChars) {

										if (currentTranslateListWithoutPolishChars.toLowerCase(Locale.getDefault()).indexOf(wordWithoutPolishCharsToLowerCase) != -1) {
											addDictionaryEntry = true;

											break;
										}
									}
								}						
							}

							if (addDictionaryEntry == false && findWordRequest.searchInfo == true) {

								if (infoString != null) {

									if (infoString.toLowerCase(Locale.getDefault()).indexOf(wordToLowerCase) != -1) {
										addDictionaryEntry = true;
									}

									if (addDictionaryEntry == false) {								
										String infoStringWithoutPolishChars = document.get(LuceneStatic.dictionaryEntry_infoWithoutPolishChars);

										if (infoStringWithoutPolishChars != null) {
											if (infoStringWithoutPolishChars.toLowerCase(Locale.getDefault()).indexOf(wordWithoutPolishCharsToLowerCase) != -1) {
												addDictionaryEntry = true;
											}									
										}
									}
								}
							}						

							if (addDictionaryEntry == true) {
								
								List<String> exampleSentenceGroupIdsList = Arrays.asList(document.getValues(LuceneStatic.dictionaryEntry_exampleSentenceGroupIdsList));
								
								DictionaryEntry entry = Utils.parseDictionaryEntry(idString, dictionaryEntryTypeList, attributeList,
										groupsList, prefixKanaString, kanjiString, kanaList, prefixRomajiString, romajiList,
										translateList, infoString, exampleSentenceGroupIdsList);

								findWordResult.result.add(new FindWordResult.ResultItem(entry));

								if (findWordResult.result.size() > maxResult) {
									break;
								}
							}
						}
					}
				}
			}		
			*/

			if (findWordResult.result.size() > maxResult) {

				findWordResult.moreElemetsExists = true;

				findWordResult.result.remove(findWordResult.result.size() - 1);
			}

		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas wyszukiwania słówek: " + e);
		}

		return findWordResult;
	}
	
	/*
	private String removeSpecialChars(String word) {
		
		final String replaceChars = "`~!@#$%^&*()-=_+[]\\{}|;':\",./<>?";
		
        for (int idx = 0; idx < replaceChars.length(); ++idx) {
        	word = word.replaceAll("\\" + replaceChars.charAt(idx), "");
		}

		return word;		
	}
	*/

	/*
	@Override
	public DictionaryEntry getDictionaryEntryById(String id) throws DictionaryException {
		
		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.dictionaryEntry_objectType));

		query.add(phraseQuery, Occur.MUST);

		query.add(NumericRangeQuery.newIntRange(LuceneStatic.dictionaryEntry_id, Integer.parseInt(id), Integer.parseInt(id), true, true), Occur.MUST);

		return getCommonDictionaryEntryByIdUniqueKey(query);
	}

	@Override
	public DictionaryEntry getDictionaryEntryByUniqueKey(String uniqueKey) throws DictionaryException {
		
		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.dictionaryEntry_objectType));

		query.add(phraseQuery, Occur.MUST);

		query.add(createQuery(uniqueKey, LuceneStatic.dictionaryEntry_uniqueKey, WordPlaceSearch.EXACT), Occur.MUST);

		return getCommonDictionaryEntryByIdUniqueKey(query);
	}
	
	private DictionaryEntry getCommonDictionaryEntryByIdUniqueKey(BooleanQuery query) throws DictionaryException {
				
		try {
			ScoreDoc[] scoreDocs = searcher.search(query, null, 1).scoreDocs;

			if (scoreDocs.length == 0) {
				return null;
			}

			Document foundDocument = searcher.doc(scoreDocs[0].doc);

			String idString = foundDocument.get(LuceneStatic.dictionaryEntry_id);

			List<String> dictionaryEntryTypeList = Arrays.asList(foundDocument.getValues(LuceneStatic.dictionaryEntry_dictionaryEntryTypeList));
			List<String> attributeList = Arrays.asList(foundDocument.getValues(LuceneStatic.dictionaryEntry_attributeList));
			List<String> groupsList = Arrays.asList(foundDocument.getValues(LuceneStatic.dictionaryEntry_groupsList));

			String prefixKanaString = foundDocument.get(LuceneStatic.dictionaryEntry_prefixKana);

			String kanjiString = foundDocument.get(LuceneStatic.dictionaryEntry_kanji);
			String kana = foundDocument.get(LuceneStatic.dictionaryEntry_kana);

			String prefixRomajiString = foundDocument.get(LuceneStatic.dictionaryEntry_prefixRomaji);

			String romaji = foundDocument.get(LuceneStatic.dictionaryEntry_romaji);				
			List<String> translateList = Arrays.asList(foundDocument.getValues(LuceneStatic.dictionaryEntry_translatesList));

			String infoString = foundDocument.get(LuceneStatic.dictionaryEntry_info);
			
			List<String> exampleSentenceGroupIdsList = Arrays.asList(foundDocument.getValues(LuceneStatic.dictionaryEntry_exampleSentenceGroupIdsList));

			return Utils.parseDictionaryEntry(idString, dictionaryEntryTypeList, attributeList,
					groupsList, prefixKanaString, kanjiString, kana, prefixRomajiString, romaji,
					translateList, infoString, exampleSentenceGroupIdsList);

		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas pobierania słowa: " + e);
		}		
	}
	*/

	@Override
	public JMdict.Entry getDictionaryEntry2ById(Integer entryId) throws DictionaryException {
				
		Gson gson = new Gson();

		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.dictionaryEntry2_objectType));

		query.add(phraseQuery, Occur.MUST);

		query.add(NumericRangeQuery.newIntRange(LuceneStatic.dictionaryEntry2_id, entryId, entryId, true, true), Occur.MUST);

		try {
			ScoreDoc[] scoreDocs = searcher.search(query, null, 1).scoreDocs;

			if (scoreDocs.length == 0) {
				return null;
			}

			Document foundDocument = searcher.doc(scoreDocs[0].doc);

			//String idString = foundDocument.get(LuceneStatic.dictionaryEntry2_id);
			String entryBody = foundDocument.get(LuceneStatic.dictionaryEntry2_entry);
			
			return gson.fromJson(entryBody, JMdict.Entry.class);
			
		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas pobierania słowa: " + e);
		}		
	}
	
	@Override
	public JMdict.Entry getDictionaryEntry2ByCounter(int counter) throws DictionaryException {
				
		Gson gson = new Gson();

		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.dictionaryEntry2_objectType));

		query.add(phraseQuery, Occur.MUST);

		query.add(NumericRangeQuery.newIntRange(LuceneStatic.dictionaryEntry2_counter, counter, counter, true, true), Occur.MUST);

		try {
			ScoreDoc[] scoreDocs = searcher.search(query, null, 1).scoreDocs;

			if (scoreDocs.length == 0) {
				return null;
			}

			Document foundDocument = searcher.doc(scoreDocs[0].doc);

			//String idString = foundDocument.get(LuceneStatic.dictionaryEntry2_id);
			String entryBody = foundDocument.get(LuceneStatic.dictionaryEntry2_entry);
			
			return gson.fromJson(entryBody, JMdict.Entry.class);
			
		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas pobierania słowa: " + e);
		}		
	}
	
	@Override
	public JMdict.Entry getDictionaryEntry2ByOldPolishJapaneseDictionaryId(long oldPolishJapaneseDictionaryId) throws DictionaryException {
				
		Gson gson = new Gson();

		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.dictionaryEntry2_objectType));

		query.add(phraseQuery, Occur.MUST);

		query.add(NumericRangeQuery.newLongRange(LuceneStatic.dictionaryEntry2_oldPolishJapaneseDictionaryId, oldPolishJapaneseDictionaryId, oldPolishJapaneseDictionaryId, true, true), Occur.MUST);

		try {
			ScoreDoc[] scoreDocs = searcher.search(query, null, 1).scoreDocs;

			if (scoreDocs.length == 0) {
				return null;
			}

			Document foundDocument = searcher.doc(scoreDocs[0].doc);

			//String idString = foundDocument.get(LuceneStatic.dictionaryEntry2_id);
			String entryBody = foundDocument.get(LuceneStatic.dictionaryEntry2_entry);
			
			return gson.fromJson(entryBody, JMdict.Entry.class);
			
		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas pobierania słowa: " + e);
		}		
	}
	
	@Override
	public JMdict.Entry getDictionaryEntry2ByOldPolishJapaneseDictionaryUniqueKey(String uniqueKey) throws DictionaryException {
				
		Gson gson = new Gson();

		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.dictionaryEntry2_objectType));

		query.add(phraseQuery, Occur.MUST);

		query.add(createQuery(uniqueKey, LuceneStatic.dictionaryEntry2_oldPolishJapaneseDictionaryUniqueKey, WordPlaceSearch.EXACT), Occur.MUST);
		
		try {
			ScoreDoc[] scoreDocs = searcher.search(query, null, 1).scoreDocs;

			if (scoreDocs.length == 0) {
				return null;
			}

			Document foundDocument = searcher.doc(scoreDocs[0].doc);

			//String idString = foundDocument.get(LuceneStatic.dictionaryEntry2_id);
			String entryBody = foundDocument.get(LuceneStatic.dictionaryEntry2_entry);
			
			return gson.fromJson(entryBody, JMdict.Entry.class);
			
		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas pobierania słowa: " + e);
		}		
	}
	
	@Override
	public DictionaryEntry getDictionaryEntryNameById(String id) throws DictionaryException {

		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.nameDictionaryEntry_objectType));

		query.add(phraseQuery, Occur.MUST);

		query.add(NumericRangeQuery.newIntRange(LuceneStatic.nameDictionaryEntry_id, Integer.parseInt(id), Integer.parseInt(id), true, true), Occur.MUST);
		
		return getCommonDictionaryEntryNameByIdUniqueKey(query);
	}

	@Override
	public DictionaryEntry getDictionaryEntryNameByUniqueKey(String uniqueKey) throws DictionaryException {

		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.nameDictionaryEntry_objectType));

		query.add(phraseQuery, Occur.MUST);
		
		query.add(createQuery(uniqueKey, LuceneStatic.nameDictionaryEntry_uniqueKey, WordPlaceSearch.EXACT), Occur.MUST);
		
		return getCommonDictionaryEntryNameByIdUniqueKey(query);
	}

	private DictionaryEntry getCommonDictionaryEntryNameByIdUniqueKey(BooleanQuery query) throws DictionaryException {
		
		try {
			ScoreDoc[] scoreDocs = searcher.search(query, null, 1).scoreDocs;

			if (scoreDocs.length == 0) {
				return null;
			}

			Document foundDocument = searcher.doc(scoreDocs[0].doc);

			String idString = foundDocument.get(LuceneStatic.nameDictionaryEntry_id);

			List<String> dictionaryEntryTypeList = Arrays.asList(foundDocument.getValues(LuceneStatic.nameDictionaryEntry_dictionaryEntryTypeList));
			List<String> attributeList = Arrays.asList(foundDocument.getValues(LuceneStatic.nameDictionaryEntry_attributeList));
			List<String> groupsList = new ArrayList<String>();

			String prefixKanaString = "";

			String kanjiString = foundDocument.get(LuceneStatic.nameDictionaryEntry_kanji);
			String kana = foundDocument.get(LuceneStatic.nameDictionaryEntry_kana);

			String prefixRomajiString = "";

			String romaji = foundDocument.get(LuceneStatic.nameDictionaryEntry_romaji);				
			List<String> translateList = Arrays.asList(foundDocument.getValues(LuceneStatic.nameDictionaryEntry_translatesList));

			String infoString = foundDocument.get(LuceneStatic.nameDictionaryEntry_info);

			List<String> exampleSentenceGroupIdsList = new ArrayList<String>();
			
			DictionaryEntry entry = Utils.parseDictionaryEntry(idString, dictionaryEntryTypeList, attributeList,
					groupsList, prefixKanaString, kanjiString, kana, prefixRomajiString, romaji,
					translateList, infoString, exampleSentenceGroupIdsList);

			entry.setName(true);
			
			return entry;
			
		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas pobierania słowa: " + e);
		}		
	}
	
	@Override
	public DictionaryEntry getNthDictionaryEntry(int nth) throws DictionaryException {
		
		// FM_FIXME: prawdopodobnie do usuniecia lub modyfikacji

		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.dictionaryEntry_objectType));

		query.add(phraseQuery, Occur.MUST);

		try {
			ScoreDoc[] scoreDocs = searcher.search(query, null, Integer.MAX_VALUE).scoreDocs;

			if (nth < 0 || nth >= scoreDocs.length) {
				return null;
			}

			Document foundDocument = searcher.doc(scoreDocs[nth].doc);

			String idString = foundDocument.get(LuceneStatic.dictionaryEntry_id);

			List<String> dictionaryEntryTypeList = Arrays.asList(foundDocument.getValues(LuceneStatic.dictionaryEntry_dictionaryEntryTypeList));
			List<String> attributeList = Arrays.asList(foundDocument.getValues(LuceneStatic.dictionaryEntry_attributeList));
			List<String> groupsList = Arrays.asList(foundDocument.getValues(LuceneStatic.dictionaryEntry_groupsList));

			String prefixKanaString = foundDocument.get(LuceneStatic.dictionaryEntry_prefixKana);

			String kanjiString = foundDocument.get(LuceneStatic.dictionaryEntry_kanji);
			String kana = foundDocument.get(LuceneStatic.dictionaryEntry_kana);

			String prefixRomajiString = foundDocument.get(LuceneStatic.dictionaryEntry_prefixRomaji);

			String romaji = foundDocument.get(LuceneStatic.dictionaryEntry_romaji);				
			List<String> translateList = Arrays.asList(foundDocument.getValues(LuceneStatic.dictionaryEntry_translatesList));

			String infoString = foundDocument.get(LuceneStatic.dictionaryEntry_info);
			
			List<String> exampleSentenceGroupIdsList = Arrays.asList(foundDocument.getValues(LuceneStatic.dictionaryEntry_exampleSentenceGroupIdsList));

			return Utils.parseDictionaryEntry(idString, dictionaryEntryTypeList, attributeList,
					groupsList, prefixKanaString, kanjiString, kana, prefixRomajiString, romaji,
					translateList, infoString, exampleSentenceGroupIdsList);

		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas pobierania n-tego słowa: " + e);
		}				
	}

	private Query createQuery(String rejoinedWord, String[] wordSplited, String fieldName, WordPlaceSearch wordPlaceSearch, boolean useSpanFirstQuery) {

		if (wordPlaceSearch == WordPlaceSearch.START_WITH) {
			
			Query exactQuery;
			
			if (useSpanFirstQuery == false) {
				exactQuery = new BooleanQuery();
				
				for (String currentWord : wordSplited) {
					((BooleanQuery)exactQuery).add(new TermQuery(new Term(fieldName, currentWord)), Occur.MUST);
					
					exactQuery.setBoost(2.0f);
				}

				
			} else {
				exactQuery = new SpanFirstQuery(new SpanTermQuery(new Term(fieldName, rejoinedWord)), 1);
				
				exactQuery.setBoost(2.0f);
			}			
						
			BooleanQuery startWithQuery = new BooleanQuery();
			
			for (String currentWord : wordSplited) {
				startWithQuery.add(new PrefixQuery(new Term(fieldName, currentWord)), Occur.MUST);
			}
			
			BooleanQuery booleanQuery = new BooleanQuery();
			
			booleanQuery.add(exactQuery, Occur.SHOULD);
			booleanQuery.add(startWithQuery, Occur.MUST);

			return booleanQuery;
			
		} else if (wordPlaceSearch == WordPlaceSearch.ANY_PLACE) {
			
			BooleanQuery exactQuery = new BooleanQuery();
			
			for (String currentWord : wordSplited) {
				exactQuery.add(new TermQuery(new Term(fieldName + "_" + LuceneStatic.prefix, currentWord)), Occur.MUST);
			}

			BooleanQuery startWithQuery = new BooleanQuery();
			
			for (String currentWord : wordSplited) {
				startWithQuery.add(new PrefixQuery(new Term(fieldName + "_" + LuceneStatic.prefix, currentWord)), Occur.MUST);
			}
			
			BooleanQuery booleanQuery = new BooleanQuery();
			
			booleanQuery.add(exactQuery, Occur.SHOULD);
			booleanQuery.add(startWithQuery, Occur.MUST);

			return booleanQuery;
			
		} else if (wordPlaceSearch == WordPlaceSearch.EXACT) {

			BooleanQuery booleanQuery = new BooleanQuery();
			
			for (String currentWord : wordSplited) {
				booleanQuery.add(new TermQuery(new Term(fieldName, currentWord)), Occur.MUST);
			}
			
			return booleanQuery;

		} else {
			throw new RuntimeException();
		}
	}

	private Query createQuery(String word, String fieldName, WordPlaceSearch wordPlaceSearch) {

		if (wordPlaceSearch == WordPlaceSearch.START_WITH) {
			
			BooleanQuery exactQuery = new BooleanQuery();			
			exactQuery.add(new TermQuery(new Term(fieldName, word)), Occur.MUST);

			BooleanQuery startWithQuery = new BooleanQuery();
			startWithQuery.add(new PrefixQuery(new Term(fieldName, word)), Occur.MUST);
			
			BooleanQuery booleanQuery = new BooleanQuery();
			
			booleanQuery.add(exactQuery, Occur.SHOULD);
			booleanQuery.add(startWithQuery, Occur.MUST);

			return booleanQuery;
			
		} else if (wordPlaceSearch == WordPlaceSearch.ANY_PLACE) {
			
			BooleanQuery exactQuery = new BooleanQuery();			
			exactQuery.add(new TermQuery(new Term(fieldName + "_" + LuceneStatic.prefix, word)), Occur.MUST);

			BooleanQuery startWithQuery = new BooleanQuery();
			startWithQuery.add(new PrefixQuery(new Term(fieldName + "_" + LuceneStatic.prefix, word)), Occur.MUST);
			
			BooleanQuery booleanQuery = new BooleanQuery();
			
			booleanQuery.add(exactQuery, Occur.SHOULD);
			booleanQuery.add(startWithQuery, Occur.MUST);

			return booleanQuery;			
			
		} else if (wordPlaceSearch == WordPlaceSearch.EXACT) {
			
			Query query = new TermQuery(new Term(fieldName, word));
			
			return query;

		} else {
			throw new RuntimeException();
		}
	}
	
	private BooleanQuery createDictionaryEntryTypeListFilter(String typeFieldName, List<DictionaryEntryType> dictionaryEntryTypeList) {

		if (dictionaryEntryTypeList == null) {			
			return null;
		}

		BooleanQuery booleanQuery = new BooleanQuery();
		
		for (DictionaryEntryType dictionaryEntryType : dictionaryEntryTypeList) {
			booleanQuery.add(createQuery(dictionaryEntryType.toString(), typeFieldName, WordPlaceSearch.EXACT), Occur.SHOULD);
		}			
		
		return booleanQuery;
	}

	@Override
	public int getDictionaryEntriesSize() {
		
		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.dictionaryEntry2_objectType));

		query.add(phraseQuery, Occur.MUST);

		try {
			return searcher.search(query, null, Integer.MAX_VALUE).scoreDocs.length;			

		} catch (IOException e) {
			throw new RuntimeException("Błąd podczas pobierania liczby słówek: " + e);
		}		
	}

	@Override
	public int getDictionaryEntriesNameSize() {

		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.nameDictionaryEntry_objectType));

		query.add(phraseQuery, Occur.MUST);

		try {
			return searcher.search(query, null, Integer.MAX_VALUE).scoreDocs.length;			

		} catch (IOException e) {
			throw new RuntimeException("Błąd podczas pobierania liczby słówek: " + e);
		}		
	}
	
	@Override
	public List<GroupEnum> getDictionaryEntryGroupTypes() {

		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.uniqueDictionaryEntryGroupEnumList_objectType));

		query.add(phraseQuery, Occur.MUST);

		Set<String> uniqueGroupStringTypes = new HashSet<String>();

		try {			
			ScoreDoc[] scoreDocs = searcher.search(query, null, Integer.MAX_VALUE).scoreDocs;

			for (ScoreDoc scoreDoc : scoreDocs) {

				Document foundDocument = searcher.doc(scoreDoc.doc);

				uniqueGroupStringTypes.addAll(Arrays.asList(foundDocument.getValues(LuceneStatic.uniqueDictionaryEntryGroupEnumList_groupsList)));
			}

			List<GroupEnum> result = GroupEnum.convertToListGroupEnum(new ArrayList<String>(uniqueGroupStringTypes));

			GroupEnum.sortGroups(result);

			return result;

		} catch (IOException e) {
			throw new RuntimeException("Błąd podczas pobierania typów grup słówek: " + e);
		}		
	}

	@Override
	public List<Entry> getGroupDictionaryEntry2List(GroupEnum groupEnum) throws DictionaryException {
		
		List<Entry> result = new ArrayList<>();
		Gson gson = new Gson();

		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.dictionaryEntry2_objectType));

		query.add(phraseQuery, Occur.MUST);

		query.add(createQuery(groupEnum.name(), LuceneStatic.dictionaryEntry2_groupsList, WordPlaceSearch.EXACT), Occur.MUST);
		
		try {
			ScoreDoc[] scoreDocs = searcher.search(query, null, Integer.MAX_VALUE).scoreDocs;

			for (ScoreDoc scoreDoc : scoreDocs) {

				Document foundDocument = searcher.doc(scoreDoc.doc);
				
				String entryBody = foundDocument.get(LuceneStatic.dictionaryEntry2_entry);
				
				result.add(gson.fromJson(entryBody, JMdict.Entry.class));				
			}
			
			return result;			
			
		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas pobierania słów: " + e);
		}		
	}

	@Override
	public FindKanjiResult findKanji(FindKanjiRequest findKanjiRequest) throws DictionaryException {
		
		Gson gson = new Gson();

		FindKanjiResult findKanjiResult = new FindKanjiResult();
		findKanjiResult.result = new ArrayList<KanjiCharacterInfo>();

		final int maxResult = MAX_KANJI_RESULT;
		
		String[] wordSplited = getTokenizedWords(analyzerWithoutPolishChars, findKanjiRequest.word);
		String rejoinedWord = createRejoinedWord(wordSplited);
		
		try {
			//if (findKanjiRequest.wordPlaceSearch != FindKanjiRequest.WordPlaceSearch.ANY_PLACE) {

			BooleanQuery query = new BooleanQuery();

			// object type
			PhraseQuery phraseQuery = new PhraseQuery();
			phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.kanjiEntry_objectType));

			query.add(phraseQuery, Occur.MUST);

			BooleanQuery kanjiBooleanQuery = new BooleanQuery();

			// kanji
			kanjiBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.kanjiEntry_kanji, findKanjiRequest.wordPlaceSearch, false), Occur.SHOULD);				

			// translate
			//kanjiBooleanQuery.add(createQuery(wordSplitedToLowerCase, LuceneStatic.kanjiEntry_polishTranslatesList, findKanjiRequest.wordPlaceSearch), Occur.SHOULD);

			kanjiBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.kanjiEntry_polishTranslatesListWithoutPolishChars, 
					findKanjiRequest.wordPlaceSearch, true), Occur.SHOULD);

			// info
			//kanjiBooleanQuery.add(createQuery(wordSplitedToLowerCase, LuceneStatic.kanjiEntry_info, findKanjiRequest.wordPlaceSearch), Occur.SHOULD);

			kanjiBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.kanjiEntry_infoWithoutPolishChars, 
					findKanjiRequest.wordPlaceSearch, true), Occur.SHOULD);

			query.add(kanjiBooleanQuery, Occur.MUST);
			
			// range
			Integer strokeCountFrom = findKanjiRequest.strokeCountFrom;
			Integer strokeCountTo = findKanjiRequest.strokeCountTo;
							
			if (strokeCountFrom != null || strokeCountTo != null) {
				query.add(NumericRangeQuery.newIntRange(LuceneStatic.kanjiEntry_strokeCount, 
						strokeCountFrom != null ? strokeCountFrom.intValue() : 0,
						strokeCountTo != null ? strokeCountTo.intValue() : 999999, true, true), Occur.MUST);					
			}

			// szukanie
			ScoreDoc[] scoreDocs = searcher.search(query, null, maxResult + 1).scoreDocs;

			// przygodowanie odpowiedzi
			for (ScoreDoc scoreDoc : scoreDocs) {
				Document foundDocument = searcher.doc(scoreDoc.doc);
								
				KanjiCharacterInfo kanjiCharacterInfo = createKanjiEntryFromLuceneDocument(gson, foundDocument, false);

				findKanjiResult.result.add(kanjiCharacterInfo);
			}				

			/*
			} else { // findKanjiRequest.wordPlaceSearch == FindKanjiRequest.WordPlaceSearch.ANY_PLACE

				for (int docId = 0; docId < reader.maxDoc(); docId++) {

					Document document = reader.document(docId);

					String objectType = document.get(LuceneStatic.objectType);

					if (objectType.equals(LuceneStatic.kanjiEntry_objectType) == false) {
						continue;
					}

					String idString = document.get(LuceneStatic.kanjiEntry_id);

					String kanjiString = document.get(LuceneStatic.kanjiEntry_kanji);

					String strokeCountString = document.get(LuceneStatic.kanjiEntry_kanjiDic2Entry_strokeCount);

					List<String> strokePathsList = Arrays.asList(document.getValues(LuceneStatic.kanjiEntry_kanjivgEntry_strokePaths));

					String generated = document.get(LuceneStatic.kanjiEntry_generated);

					List<String> radicalsList = Arrays.asList(document.getValues(LuceneStatic.kanjiEntry_kanjiDic2Entry_radicalsList));

					List<String> onReadingList = Arrays.asList(document.getValues(LuceneStatic.kanjiEntry_kanjiDic2Entry_onReadingList));
					List<String> kunReadingList = Arrays.asList(document.getValues(LuceneStatic.kanjiEntry_kanjiDic2Entry_kunReadingList));

					List<String> polishTranslateList = Arrays.asList(document.getValues(LuceneStatic.kanjiEntry_polishTranslatesList));

					List<String> groupsList = Arrays.asList(document.getValues(LuceneStatic.kanjiEntry_groupsList));

					String infoString = document.get(LuceneStatic.kanjiEntry_info);

					boolean addDictionaryEntry = false;

					// kanji
					if (kanjiString.indexOf(findKanjiRequest.word) != -1) {
						addDictionaryEntry = true;
					}

					// translate
					if (addDictionaryEntry == false) {

						for (String currentPolishTranslate : polishTranslateList) {

							if (currentPolishTranslate.toLowerCase(Locale.getDefault()).indexOf(wordToLowerCase) != -1) {
								addDictionaryEntry = true;

								break;
							}							
						}						

						List<String> polishTranslateListWithoutPolishChars = Arrays.asList(document.getValues(LuceneStatic.kanjiEntry_polishTranslatesListWithoutPolishChars));

						if (polishTranslateListWithoutPolishChars != null && polishTranslateListWithoutPolishChars.size() > 0) {

							for (String currentPolishTranslateListWithoutPolishChars : polishTranslateListWithoutPolishChars) {

								if (currentPolishTranslateListWithoutPolishChars.toLowerCase(Locale.getDefault()).indexOf(wordWithoutPolishCharsToLowerCase) != -1) {
									addDictionaryEntry = true;

									break;
								}
							}
						}						
					}					

					// info
					if (addDictionaryEntry == false) {

						if (infoString != null) {

							if (infoString.toLowerCase(Locale.getDefault()).indexOf(wordToLowerCase) != -1) {
								addDictionaryEntry = true;
							}

							if (addDictionaryEntry == false) {								
								String infoStringWithoutPolishChars = document.get(LuceneStatic.kanjiEntry_infoWithoutPolishChars);

								if (infoStringWithoutPolishChars != null) {
									if (infoStringWithoutPolishChars.toLowerCase(Locale.getDefault()).indexOf(wordWithoutPolishCharsToLowerCase) != -1) {
										addDictionaryEntry = true;
									}									
								}
							}
						}
					}

					if (addDictionaryEntry == true) {
						KanjiEntry kanjiEntry = Utils.parseKanjiEntry(idString, kanjiString, strokeCountString, radicalsList,
								onReadingList, kunReadingList, strokePathsList, polishTranslateList, infoString, generated,
								groupsList);

						findKanjiResult.result.add(kanjiEntry);

						if (findKanjiResult.result.size() > maxResult) {
							break;
						}
					}					
				}				
			}
			*/

			if (findKanjiResult.result.size() > maxResult) {

				findKanjiResult.moreElemetsExists = true;

				findKanjiResult.result.remove(findKanjiResult.result.size() - 1);
			}

		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas wyszukiwania słówek: " + e);
		}

		return findKanjiResult;
	}

	@Override
	public Set<String> findAllAvailableRadicals(String[] radicals) throws DictionaryException {

		Set<String> result = new HashSet<String>();

		try {
			if (radicals == null || radicals.length == 0) {

				BooleanQuery query = new BooleanQuery();

				// object type
				PhraseQuery phraseQuery = new PhraseQuery();
				phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.allAvailableKanjiRadicals_objectType));

				query.add(phraseQuery, Occur.MUST);

				ScoreDoc[] scoreDocs = searcher.search(query, null, Integer.MAX_VALUE).scoreDocs;

				for (ScoreDoc scoreDoc : scoreDocs) {

					Document foundDocument = searcher.doc(scoreDoc.doc);

					result.addAll(Arrays.asList(foundDocument.getValues(LuceneStatic.allAvailableKanjiRadicals_radicalsList)));
				}

				return result;

			} else {

				BooleanQuery query = new BooleanQuery();

				// object type
				PhraseQuery phraseQuery = new PhraseQuery();
				phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.kanjiEntry_objectType));

				query.add(phraseQuery, Occur.MUST);

				BooleanQuery kanjiBooleanQuery = new BooleanQuery();

				for (String currentRadical : radicals) {
					kanjiBooleanQuery.add(createQuery(currentRadical, LuceneStatic.kanjiEntry_radicalsList, WordPlaceSearch.EXACT), Occur.MUST);
				}

				query.add(kanjiBooleanQuery, Occur.MUST);			

				ScoreDoc[] scoreDocs = searcher.search(query, null, Integer.MAX_VALUE).scoreDocs;

				for (ScoreDoc scoreDoc : scoreDocs) {

					Document foundDocument = searcher.doc(scoreDoc.doc);

					result.addAll(Arrays.asList(foundDocument.getValues(LuceneStatic.kanjiEntry_radicalsList)));
				}

				return result;

			}

		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas wyszukiwania wszystkich dostępnych znaków podstawowych: " + e);
		}
	}

	@Override
	public void findDictionaryEntriesInGrammaFormAndExamples(FindWordRequest findWordRequest, FindWordResult findWordResult)
			throws DictionaryException {
				
		if (findWordRequest.searchGrammaFormAndExamples == false) {
			return;
		}

		if (findWordResult.moreElemetsExists == true) {
			return;
		}
		
		/*
		if (findWordRequest.getWordPlaceSearch() == WordPlaceSearch.ANY_PLACE) {
			return;
		}
		*/
		
		Gson gson = new Gson();
		
		final int maxResult = MAX_DICTIONARY_RESULT - findWordResult.result.size();
		
		if (maxResult <= 0) {
			return;
		}
		
		String[] wordSplited = getTokenizedWords(analyzerWithoutPolishChars, findWordRequest.word);
		String rejoinedWord = createRejoinedWord(wordSplited);
		
		try {
			
			BooleanQuery query = new BooleanQuery();

			// object type
			PhraseQuery objectTypeQuery = new PhraseQuery();
			objectTypeQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.dictionaryEntry2_objectType));

			query.add(objectTypeQuery, Occur.MUST);
			
			BooleanQuery wordBooleanQuery = new BooleanQuery();

			if (findWordRequest.searchKanji == true) {
				wordBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.dictionaryEntry2_grammaConjufateResult_and_exampleResult_kanji, findWordRequest.wordPlaceSearch, false), Occur.SHOULD);				
			}

			if (findWordRequest.searchKana == true) {
				wordBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.dictionaryEntry2_grammaConjufateResult_and_exampleResult_kanaList, findWordRequest.wordPlaceSearch, false), Occur.SHOULD);				
			}

			if (findWordRequest.searchRomaji == true) {
				wordBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.dictionaryEntry2_grammaConjufateResult_and_exampleResult_romajiList, findWordRequest.wordPlaceSearch, true), Occur.SHOULD);				
			}

			query.add(wordBooleanQuery, Occur.MUST);

			ScoreDoc[] scoreDocs = searcher.search(query, null, maxResult).scoreDocs;

			for (ScoreDoc scoreDoc : scoreDocs) {

				Document foundDocument = searcher.doc(scoreDoc.doc);

				// dodajemy tylko te wpisy, ktorych jeszcze nie bylo
				String idString = foundDocument.get(LuceneStatic.dictionaryEntry2_id);
				int idStringAsInt = Integer.parseInt(idString);
				
				boolean alreadyExistsInResult = findWordResult.result.stream().filter(f -> f.getEntry() != null && f.getEntry().getEntryId().intValue() == idStringAsInt).count() > 0;
				
				if (alreadyExistsInResult == false) { // jezeli nie ma to sprawdzamy dalej
					
					// zamieniamy na Entry
					String entryBody = foundDocument.get(LuceneStatic.dictionaryEntry2_entry);
					
					JMdict.Entry entry = gson.fromJson(entryBody, JMdict.Entry.class);
					
					// czy dodac pozycje
					boolean addDictionaryEntry = true;
					
					MiscInfo misc = entry.getMisc();
					
					if (misc != null) {
						OldPolishJapaneseDictionaryInfo oldPolishJapaneseDictionary = misc.getOldPolishJapaneseDictionary();
						
						if (oldPolishJapaneseDictionary != null) {
							List<OldPolishJapaneseDictionaryInfoAttributeListInfo> oldPolishJapaneseDictionaryAttributeList = oldPolishJapaneseDictionary.getAttributeList();
							
							if (findWordRequest.searchOnlyCommonWord == true) { // sprawdzenie, czy wystepuje atrybut COMMON_WORD
								
								boolean existsAttributeCommonWord = oldPolishJapaneseDictionaryAttributeList.stream().filter(f -> f.getType().equals(AttributeType.COMMON_WORD.name())).count() > 0;
								
								if (existsAttributeCommonWord == false) {
									addDictionaryEntry = false;
									
									break;
								}									
							}
						}
					}
					
					// jezeli mozna dodac to sprawdzamy dalej
					if (addDictionaryEntry == true) {
						
						// sprawdzenie typow
						if (findWordRequest.dictionaryEntryTypeList != null && findWordRequest.dictionaryEntryTypeList.size() > 0) {
							
							boolean isInNeededDictionaryEntryTypeList = false;
														
							if (misc != null) {
								OldPolishJapaneseDictionaryInfo oldPolishJapaneseDictionary = misc.getOldPolishJapaneseDictionary();
								
								if (oldPolishJapaneseDictionary != null) {
									
									FOR_OLD_ENTRY:
									for (OldPolishJapaneseDictionaryInfoEntriesInfo oldEntry : oldPolishJapaneseDictionary.getEntries()) {
										List<DictionaryEntryType> dictionaryEntryTypeList = 
												Arrays.asList(oldEntry.getDictionaryEntryTypeList().split(",")).stream().map(m -> DictionaryEntryType.valueOf(m)).collect(Collectors.toList());
										
										for (DictionaryEntryType dictionaryEntryType : dictionaryEntryTypeList) {
											
											if (findWordRequest.dictionaryEntryTypeList.contains(dictionaryEntryType) == true) {
												isInNeededDictionaryEntryTypeList = true;
												
												break FOR_OLD_ENTRY;
											}
										}
									}
								}
							}
							
							if (isInNeededDictionaryEntryTypeList == false) {
								addDictionaryEntry = false;
							}							
						}						
					}
					
					if (addDictionaryEntry == true) {
						
						boolean alreadyInResult = false;
														
						if (alreadyInResult == false) {
							findWordResult.foundGrammaAndExamples = true;
							
							findWordResult.result.add(new FindWordResult.ResultItem(entry, false, true));						
						}					
					}
				}
			}			
			
		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas wyszukiwania odmian gramatycznych i przykładów: " + e);
		}
	}
	
	@Override
	public void findDictionaryEntriesInNames(FindWordRequest findWordRequest, FindWordResult findWordResult) throws DictionaryException {
		
		if (findWordRequest.searchName == false) {
			return;
		}

		if (findWordResult.moreElemetsExists == true) {
			return;
		}
		
		/*
		if (findWordRequest.getWordPlaceSearch() == WordPlaceSearch.ANY_PLACE) {
			return;
		}
		*/
		
		final int maxResult = MAX_DICTIONARY_RESULT - findWordResult.result.size();

		if (maxResult <= 0) {
			return;
		}
		
		String[] wordSplited = getTokenizedWords(analyzerWithoutPolishChars, findWordRequest.word);
		String rejoinedWord = createRejoinedWord(wordSplited);
				
		try {
			
			BooleanQuery query = new BooleanQuery();

			// object type
			PhraseQuery objectTypeQuery = new PhraseQuery();
			objectTypeQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.nameDictionaryEntry_objectType));

			query.add(objectTypeQuery, Occur.MUST);
			
			BooleanQuery wordBooleanQuery = new BooleanQuery();

			if (findWordRequest.searchKanji == true) {
				wordBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.nameDictionaryEntry_kanji, findWordRequest.wordPlaceSearch, false), Occur.SHOULD);				
			}

			if (findWordRequest.searchKana == true) {
				wordBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.nameDictionaryEntry_kana, findWordRequest.wordPlaceSearch, false), Occur.SHOULD);				
			}

			if (findWordRequest.searchRomaji == true) {
				wordBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.nameDictionaryEntry_romaji, findWordRequest.wordPlaceSearch, true), Occur.SHOULD);
				wordBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.nameDictionaryEntry_virtual_romaji, findWordRequest.wordPlaceSearch, true), Occur.SHOULD);
			}

			if (findWordRequest.searchTranslate == true) {
				//wordBooleanQuery.add(createQuery(wordSplitedToLowerCase, LuceneStatic.dictionaryEntry_translatesList, findWordRequest.wordPlaceSearch), Occur.SHOULD);

				wordBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.nameDictionaryEntry_translatesListWithoutPolishChars, 
						findWordRequest.wordPlaceSearch, true), Occur.SHOULD);
			}

			if (findWordRequest.searchInfo == true) {
				//wordBooleanQuery.add(createQuery(wordSplitedToLowerCase, LuceneStatic.dictionaryEntry_info, findWordRequest.wordPlaceSearch), Occur.SHOULD);

				wordBooleanQuery.add(createQuery(rejoinedWord, wordSplited, LuceneStatic.nameDictionaryEntry_infoWithoutPolishChars, 
						findWordRequest.wordPlaceSearch, true), Occur.SHOULD);
			}

			BooleanQuery dictionaryEntryTypeListFilter = createDictionaryEntryTypeListFilter(LuceneStatic.nameDictionaryEntry_dictionaryEntryTypeList, findWordRequest.dictionaryEntryTypeList);
			
			if (dictionaryEntryTypeListFilter != null) {
				query.add(dictionaryEntryTypeListFilter, Occur.MUST);
			}			
			
			query.add(wordBooleanQuery, Occur.MUST);			
			
			ScoreDoc[] scoreDocs = searcher.search(query, null, maxResult).scoreDocs;

			for (ScoreDoc scoreDoc : scoreDocs) {

				Document foundDocument = searcher.doc(scoreDoc.doc);
				
				String idString = foundDocument.get(LuceneStatic.nameDictionaryEntry_id);

				List<String> dictionaryEntryTypeList = Arrays.asList(foundDocument.getValues(LuceneStatic.nameDictionaryEntry_dictionaryEntryTypeList));
				List<String> attributeList = Arrays.asList(foundDocument.getValues(LuceneStatic.nameDictionaryEntry_attributeList));
				List<String> groupsList = new ArrayList<String>();

				String prefixKanaString = "";

				String kanjiString = foundDocument.get(LuceneStatic.nameDictionaryEntry_kanji);
				String kana = foundDocument.get(LuceneStatic.nameDictionaryEntry_kana);

				String prefixRomajiString = "";

				String romaji = foundDocument.get(LuceneStatic.nameDictionaryEntry_romaji);				
				List<String> translateList = Arrays.asList(foundDocument.getValues(LuceneStatic.nameDictionaryEntry_translatesList));

				String infoString = foundDocument.get(LuceneStatic.nameDictionaryEntry_info);

				List<String> exampleSentenceGroupIdsList = new ArrayList<String>();
				
				DictionaryEntry entry = Utils.parseDictionaryEntry(idString, dictionaryEntryTypeList, attributeList,
						groupsList, prefixKanaString, kanjiString, kana, prefixRomajiString, romaji,
						translateList, infoString, exampleSentenceGroupIdsList);

				entry.setName(true);
				
				findWordResult.result.add(new FindWordResult.ResultItem(entry, true, false));
				
				findWordResult.setFoundNames(true);
			}			
			
		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas wyszukiwania odmian gramatycznych i przykładów: " + e);
		}		
	}

	@Override
	public List<KanjiCharacterInfo> findKanjiFromRadicals(String[] radicals) throws DictionaryException {
		
		Gson gson = new Gson();

		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.kanjiEntry_objectType));

		query.add(phraseQuery, Occur.MUST);

		BooleanQuery kanjiBooleanQuery = new BooleanQuery();

		for (String currentRadical : radicals) {
			kanjiBooleanQuery.add(createQuery(currentRadical, LuceneStatic.kanjiEntry_radicalsList, WordPlaceSearch.EXACT), Occur.MUST);
		}

		query.add(kanjiBooleanQuery, Occur.MUST);

		List<KanjiCharacterInfo> result = new ArrayList<KanjiCharacterInfo>();

		try {
			ScoreDoc[] scoreDocs = searcher.search(query, null, Integer.MAX_VALUE).scoreDocs;

			for (ScoreDoc scoreDoc : scoreDocs) {

				Document foundDocument = searcher.doc(scoreDoc.doc);
				
				KanjiCharacterInfo kanjiCharacterInfo = createKanjiEntryFromLuceneDocument(gson, foundDocument, false);
				
				result.add(kanjiCharacterInfo);

			}

			return result;

		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas wyszukiwania wszystkich znaków kanji po znaków podstawowych: " + e);
		}		
	}

	@Override
	public FindKanjiResult findKanjisFromStrokeCount(int from, int to) throws DictionaryException {
		
		Gson gson = new Gson();

		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.kanjiEntry_objectType));

		query.add(phraseQuery, Occur.MUST);

		query.add(NumericRangeQuery.newIntRange(LuceneStatic.kanjiEntry_strokeCount, from, to, true, true), Occur.MUST);

		final int maxResult = MAX_KANJI_STROKE_COUNT_RESULT;

		try {

			ScoreDoc[] scoreDocs = searcher.search(query, null, maxResult + 1).scoreDocs;

			List<KanjiCharacterInfo> result = new ArrayList<KanjiCharacterInfo>();

			for (ScoreDoc scoreDoc : scoreDocs) {

				Document foundDocument = searcher.doc(scoreDoc.doc);
				
				KanjiCharacterInfo kanjiCharacterInfo = createKanjiEntryFromLuceneDocument(gson, foundDocument, false);
			    				
				result.add(kanjiCharacterInfo);
			}				

			FindKanjiResult findKanjiResult = new FindKanjiResult();

			if (result.size() >= maxResult) {
				result.remove(result.size() - 1);

				findKanjiResult.setMoreElemetsExists(true);
			}

			findKanjiResult.setResult(result);

			return findKanjiResult;

		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas wyszukiwania wszystkich znaków kanji po ilościach kresek: " + e);
		}
	}

	@Override
	public KanjiCharacterInfo getKanjiEntry(String kanji) throws DictionaryException {

		Gson gson = new Gson();
		
		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.kanjiEntry_objectType));

		query.add(phraseQuery, Occur.MUST);

		query.add(createQuery(kanji, LuceneStatic.kanjiEntry_kanji, WordPlaceSearch.EXACT), Occur.MUST);

		try {
			ScoreDoc[] scoreDocs = searcher.search(query, null, 1).scoreDocs;

			if (scoreDocs.length == 0) {
				return null;
			}

			Document foundDocument = searcher.doc(scoreDocs[0].doc);
			
			KanjiCharacterInfo kanjiCharacterInfo = createKanjiEntryFromLuceneDocument(gson, foundDocument, true);
						
			return kanjiCharacterInfo;

		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas pobierania znaku kanji: " + e);
		}
	}
	
	@Override
	public List<KanjiCharacterInfo> getKanjiEntryList(List<String> kanjiList) throws DictionaryException {
		
		List<KanjiCharacterInfo> result = new ArrayList<KanjiCharacterInfo>();
		
		if (kanjiList != null) {
			
			for (String currentKanji : kanjiList) {
				
				KanjiCharacterInfo kanjiCharacterInfo = getKanjiEntry(currentKanji);
				
				if (kanjiCharacterInfo != null) {
					result.add(kanjiCharacterInfo);
				}				
			}			
		}
		
		return result;
	}

	
	@Override
	public KanjiCharacterInfo getKanjiEntryById(String id) throws DictionaryException {
		
		Gson gson = new Gson();

		BooleanQuery query = new BooleanQuery();
		
		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.kanjiEntry_objectType));

		query.add(phraseQuery, Occur.MUST);
		
		query.add(NumericRangeQuery.newIntRange(LuceneStatic.kanjiEntry_id, Integer.parseInt(id), Integer.parseInt(id), true, true), Occur.MUST);
		
		try {
			ScoreDoc[] scoreDocs = searcher.search(query, null, 1).scoreDocs;

			if (scoreDocs.length == 0) {
				return null;
			}

			Document foundDocument = searcher.doc(scoreDocs[0].doc);
			
			KanjiCharacterInfo kanjiCharacterInfo = createKanjiEntryFromLuceneDocument(gson, foundDocument, true);
						
			return kanjiCharacterInfo;

		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas pobierania znaku kanji: " + e);
		}		
	}

	@Override
	public List<KanjiCharacterInfo> getAllKanjis(boolean withDetails, boolean onlyUsed) throws DictionaryException {
		
		Gson gson = new Gson();

		BooleanQuery query = new BooleanQuery();

		// object type
		PhraseQuery phraseQuery = new PhraseQuery();
		phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.kanjiEntry_objectType));

		query.add(phraseQuery, Occur.MUST);

		if (onlyUsed == true) {
			query.add(createQuery("true", LuceneStatic.kanjiEntry_used, WordPlaceSearch.EXACT), Occur.MUST);
		}

		try {

			ScoreDoc[] scoreDocs = searcher.search(query, null, Integer.MAX_VALUE).scoreDocs;

			List<KanjiCharacterInfo> result = new ArrayList<KanjiCharacterInfo>();

			for (ScoreDoc scoreDoc : scoreDocs) {

				Document foundDocument = searcher.doc(scoreDoc.doc);
				
				KanjiCharacterInfo kanjiCharacterInfo = createKanjiEntryFromLuceneDocument(gson, foundDocument, withDetails == true);
			    
				result.add(kanjiCharacterInfo);
			}
			
			Collections.sort(result, new Comparator<KanjiCharacterInfo>() {

				@Override
				public int compare(KanjiCharacterInfo o1, KanjiCharacterInfo o2) {
					
					int o1id = o1.getId();
					int o2id = o2.getId();
					
					if (o1id < o2id) {
						return -1;
						
					} else if (o1id > o2id) {
						return 1;
						
					} else {
						return 0;
					}
				}
			});

			return result;

		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas wyszukiwania wszystkich znaków kanji po ilościach kresek: " + e);
		}		
	}

	public List<String> getAutocomplete(LuceneDatabaseSuggesterAndSpellCheckerSource source, String term, int limit) throws DictionaryException {

		List<String> result = new ArrayList<String>();
		
		if (lookupSuggesterMap == null || term == null || term.length() == 0) {
			return new ArrayList<String>();
		}
		
		Lookup lookup = lookupSuggesterMap.get(source);
		
		if (lookup != null) {
			
			// ewentualna zamiana half-width kana na full-width
			term = Utils.convertHalfWidthKanaToFullKana(term);
			
			List<LookupResult> lookupResult = lookup.lookup(term, false, limit);

			for (LookupResult currentLookupResult : lookupResult) {
				result.add(currentLookupResult.key.toString());
			}			
		}

		return result;
	}
	
	public boolean isAutocompleteInitialized(LuceneDatabaseSuggesterAndSpellCheckerSource source) {
		
		if (lookupSuggesterMap == null) {
			return false;
		}
		
		Lookup lookup = lookupSuggesterMap.get(source);
				
		return lookup != null;
	}
	
	public boolean isSpellCheckerInitialized(LuceneDatabaseSuggesterAndSpellCheckerSource source) {
		
		if (spellCheckerMap == null) {
			return false;
		}
		
		SpellCheckerIndex spellCheckerIndex = spellCheckerMap.get(source);
		
		return spellCheckerIndex != null;
	}
	
	public List<String> getSpellCheckerSuggestion(LuceneDatabaseSuggesterAndSpellCheckerSource source, String term, int limit) throws DictionaryException {
		
		try {		
			List<String> result = new ArrayList<String>();
			
			if (spellCheckerMap == null || term == null || term.length() == 0) {
				return result;
			}
			
			SpellCheckerIndex spellCheckerIndex = spellCheckerMap.get(source);
			
			if (spellCheckerIndex != null) {
				
				String[] suggestSimilar = spellCheckerIndex.spellChecker.suggestSimilar(term, limit);
				
				if (suggestSimilar != null) {
					
					for (String currentSuggest : suggestSimilar) {
						result.add(currentSuggest);
					}				
				}
			}		
			
			return result;
			
		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas pobierania sugestii w poprawiaczu słów" + e);
		}
	}	
	
	@Override
	public GroupWithTatoebaSentenceList getTatoebaSentenceGroup(String groupId) throws DictionaryException {
		
		// FM_FIXME: prawdopodobnie do usuniecia lub nie, do ustalenia
		
		// znajdowanie identyfikatorow zdan		
		BooleanQuery groupQuery = new BooleanQuery();

		// object type
		PhraseQuery groupPhraseQuery = new PhraseQuery();
		groupPhraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.dictionaryEntry_exampleSentenceGroups_objectType));

		groupQuery.add(groupPhraseQuery, Occur.MUST);

		groupQuery.add(createQuery(groupId, LuceneStatic.dictionaryEntry_exampleSentenceGroups_groupId, WordPlaceSearch.EXACT), Occur.MUST);

		List<String> sentencesIdList = null;

		try {
			// szukanie
			ScoreDoc[] scoreDocs = searcher.search(groupQuery, null, 1).scoreDocs;

			for (ScoreDoc scoreDoc : scoreDocs) {

				Document foundDocument = searcher.doc(scoreDoc.doc);

				sentencesIdList = Arrays.asList(foundDocument.getValues(LuceneStatic.dictionaryEntry_exampleSentenceGroups_sentenceIdList));
			}		

			if (sentencesIdList != null && sentencesIdList.size() > 0) {	

				GroupWithTatoebaSentenceList result = new GroupWithTatoebaSentenceList();

				result.setGroupId(groupId);
				result.setTatoebaSentenceList(new ArrayList<TatoebaSentence>());

				for (String currentSentenceId : sentencesIdList) {

					// znajdowanie tresci zdan
					BooleanQuery sentenceQuery = new BooleanQuery();

					// object type
					PhraseQuery sentencePhraseQuery = new PhraseQuery();
					sentencePhraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.dictionaryEntry_exampleSentence_objectType));

					sentenceQuery.add(sentencePhraseQuery, Occur.MUST);

					sentenceQuery.add(createQuery(currentSentenceId, LuceneStatic.dictionaryEntry_exampleSentence_id, WordPlaceSearch.EXACT), Occur.MUST);

					// szukanie
					scoreDocs = searcher.search(sentenceQuery, null, 1).scoreDocs;

					for (ScoreDoc scoreDoc : scoreDocs) {

						Document foundDocument = searcher.doc(scoreDoc.doc);

						String sentenceId = foundDocument.get(LuceneStatic.dictionaryEntry_exampleSentence_id);
						String sentencelang = foundDocument.get(LuceneStatic.dictionaryEntry_exampleSentence_lang);
						String sentenceSentence = foundDocument.get(LuceneStatic.dictionaryEntry_exampleSentence_sentence);

						TatoebaSentence tatoebaSentence = new TatoebaSentence();

						tatoebaSentence.setId(sentenceId);
						tatoebaSentence.setLang(sentencelang);
						tatoebaSentence.setSentence(sentenceSentence);

						result.getTatoebaSentenceList().add(tatoebaSentence);
					}
				}

				return result;
			}		
			
		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas wyszukiwania przykładów zdań: " + e);
		}	

		return null;
	}
	
	private String[] getTokenizedWords(LuceneAnalyzer methodAnalyzer, String text) throws DictionaryException {
		
		List<String> tokenizedWordsList = new ArrayList<String>();
		
		TokenStream tokenStream = null;
		
		try {
			tokenStream = methodAnalyzer.tokenStream("fieldName", text);
			
			tokenStream.reset();
			
			while(true) {
				
				boolean incrementTokenResult = tokenStream.incrementToken();
				
				if (incrementTokenResult == false) {
					
					tokenStream.end();
										
					break;
				}
				
				tokenizedWordsList.add(Utils.convertHalfWidthKanaToFullKana(tokenStream.getAttribute(CharTermAttribute.class).toString()));
			}
			
		} catch (IOException e) {
			throw new DictionaryException("Błąd podczas tokenizowania słowa: " + text + " - " + e);
		
		} finally {
			
			if (tokenStream != null) {
				
				try {
					tokenStream.close();
					
				} catch (IOException e) {
					throw new DictionaryException("Błąd podczas tokenizowania słowa: " + text + " - " + e);
				}
			}			
			
		}
				
		return tokenizedWordsList.toArray(new String[tokenizedWordsList.size()]);
	}
	
	private String createRejoinedWord(String[] wordSpllited) {
		
		StringBuffer result = new StringBuffer();
		
		for (int idx = 0; idx < wordSpllited.length; ++idx) {
			result.append(wordSpllited[idx]);
			
			if (idx != wordSpllited.length - 1) {
				result.append(" ");
			}
		}
		
		return result.toString();
	}
	
	private KanjiCharacterInfo createKanjiEntryFromLuceneDocument(Gson gson, Document luceneDocument, boolean addStroke) throws IOException {
				
		String entryBody = luceneDocument.get(LuceneStatic.kanjiEntry_entry);
		
		KanjiCharacterInfo kanjiCharacterInfo = gson.fromJson(entryBody, KanjiCharacterInfo.class);
		
		if (addStroke == true) {
			// pobieramy strokePaths
			BooleanQuery query = new BooleanQuery();
			
			// object type
			PhraseQuery phraseQuery = new PhraseQuery();
			phraseQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.kanjiEntryStrokePaths_objectType));

			query.add(phraseQuery, Occur.MUST);
			
			query.add(NumericRangeQuery.newIntRange(LuceneStatic.kanjiEntryStrokePaths_id, kanjiCharacterInfo.getId(), kanjiCharacterInfo.getId(), true, true), Occur.MUST);
			
			ScoreDoc[] scoreDocs = searcher.search(query, null, 1).scoreDocs;

			if (scoreDocs.length > 0) {
				Document foundStrokePathsDocument = searcher.doc(scoreDocs[0].doc);
				
				String strokePathsAsString = foundStrokePathsDocument.get(LuceneStatic.kanjiEntryStrokePaths_strokePaths);
				
				if (strokePathsAsString != null) {
					List<String> strokePaths = Utils.parseStringIntoList(strokePathsAsString);
					
					if (kanjiCharacterInfo.getMisc2() == null) {
						kanjiCharacterInfo.setMisc2(new Misc2Info());
					}
					
					kanjiCharacterInfo.getMisc2().getStrokePaths().clear();
					kanjiCharacterInfo.getMisc2().getStrokePaths().addAll(strokePaths);
				}				
			}
		}
		
		return kanjiCharacterInfo;
	}
	
	private static class SpellCheckerIndex {
		
		private SpellChecker spellChecker;
		
		private Directory index;		
	}
}
