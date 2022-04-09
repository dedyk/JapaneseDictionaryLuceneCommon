package pl.idedyk.japanese.dictionary.lucene;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.spell.JaroWinklerDistance;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import pl.idedyk.japanese.dictionary.api.dictionary.Utils;
import pl.idedyk.japanese.dictionary.api.dto.Attribute;
import pl.idedyk.japanese.dictionary.api.dto.AttributeType;
import pl.idedyk.japanese.dictionary.api.dto.DictionaryEntry;
import pl.idedyk.japanese.dictionary.api.dto.DictionaryEntryType;
import pl.idedyk.japanese.dictionary.api.dto.GroupEnum;
import pl.idedyk.japanese.dictionary.api.dto.GroupWithTatoebaSentenceList;
import pl.idedyk.japanese.dictionary.api.dto.KanjiDic2Entry;
import pl.idedyk.japanese.dictionary.api.dto.KanjiEntry;
import pl.idedyk.japanese.dictionary.api.dto.KanjivgEntry;
import pl.idedyk.japanese.dictionary.api.dto.RadicalInfo;
import pl.idedyk.japanese.dictionary.api.dto.TatoebaSentence;
import pl.idedyk.japanese.dictionary.api.example.ExampleManager;
import pl.idedyk.japanese.dictionary.api.example.dto.ExampleGroupTypeElements;
import pl.idedyk.japanese.dictionary.api.example.dto.ExampleResult;
import pl.idedyk.japanese.dictionary.api.exception.DictionaryException;
import pl.idedyk.japanese.dictionary.api.gramma.GrammaConjugaterManager;
import pl.idedyk.japanese.dictionary.api.gramma.dto.GrammaFormConjugateGroupTypeElements;
import pl.idedyk.japanese.dictionary.api.gramma.dto.GrammaFormConjugateResult;
import pl.idedyk.japanese.dictionary.api.gramma.dto.GrammaFormConjugateResultType;
import pl.idedyk.japanese.dictionary.api.keigo.KeigoHelper;
import pl.idedyk.japanese.dictionary2.api.helper.Dictionary2HelperCommon;
import pl.idedyk.japanese.dictionary2.api.helper.Dictionary2HelperCommon.KanjiKanaPair;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.DialectEnum;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.FieldEnum;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.Gloss;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.JMdict;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.JMdict.Entry;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.LanguageSource;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.MiscEnum;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.PartOfSpeechEnum;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.Sense;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.SenseAdditionalInfo;

import com.csvreader.CsvReader;
import com.google.gson.Gson;

public class LuceneDBGenerator {	
	
	public static void main(String[] args) throws Exception {
		
		// android: android db/word.csv db/sentences.csv db/sentences_groups.csv db/kanji.csv db/radical.csv db/names.csv db/word2.xml db-lucene
		// web: web db/word.csv db/sentences.csv db/sentences_groups.csv db/kanji.csv db/radical.csv db/names.csv word2.xml db-lucene
		
		// parametry
		String mode = args[0];
		
		String dictionaryFilePath = args[1];
		String sentencesFilePath = args[2];
		String sentencesGroupsFilePath = args[3];
		String kanjiFilePath = args[4];
		String radicalFilePath = args[5];
		final String nameFilePath = args[6];
		
		final String word2XmlFilePath = args[7];
		
		String dbOutDir = args[8];
		
		boolean addSugestionList;
		boolean addGrammaAndExample;		
		
		boolean generateNamesDictionary;
		
		boolean generateDictionaryEntryPrefixes;
		boolean generateKanjiEntryPrefixes;
		boolean generateNameEntryPrefixes;
		
		boolean addWord2Xml;
		
		if (mode.equals("web") == true) {
			
			addSugestionList = true;
			addGrammaAndExample = true;		
			
			generateNamesDictionary = true;
			
			generateDictionaryEntryPrefixes = true;
			generateKanjiEntryPrefixes = true;
			generateNameEntryPrefixes = true;
			
			addWord2Xml = true;
			
		} else if (mode.equals("android") == true) {
			
			addSugestionList = false;
			addGrammaAndExample = false;	
			
			generateNamesDictionary = false;
			
			generateDictionaryEntryPrefixes = false;
			generateKanjiEntryPrefixes = true;
			generateNameEntryPrefixes = false;
			
			addWord2Xml = true;

		} else {
			throw new Exception("Unknown mode: " + mode);
		}
				
		////
								
		final File dbOutDirFile = new File(dbOutDir);
		
		if (dbOutDirFile.exists() == false) {
			dbOutDirFile.mkdir();
		}
		
		if (dbOutDirFile.isDirectory() == true) {
			
			File[] dbOutDirFileListFiles = dbOutDirFile.listFiles();
			
			for (File file : dbOutDirFileListFiles) {
				file.delete();
			}
		}		
		
		// tworzenie indeksu lucene
		Directory index = FSDirectory.open(dbOutDirFile);
		
		// tworzenie analizatora lucene
		LuceneAnalyzer analyzer = new LuceneAnalyzer(Version.LUCENE_47, true);
		
		// tworzenie zapisywacza konfiguracji
		IndexWriterConfig indexWriterConfig = new IndexWriterConfig(Version.LUCENE_47, analyzer);
		indexWriterConfig.setOpenMode(OpenMode.CREATE);
		
		IndexWriter indexWriter = new IndexWriter(index, indexWriterConfig);
		
		// wczytanie pliku ze slownikiem w formacie word2.xml
		JMdict jmdict = readWord2Xml(word2XmlFilePath);

		// otwarcie pliku ze slownikiem
		FileInputStream dictionaryInputStream = new FileInputStream(dictionaryFilePath);

		// wczytywanie slownika
		List<DictionaryEntry> dictionaryEntryList = readDictionaryFile(indexWriter, dictionaryInputStream, jmdict, addSugestionList, generateDictionaryEntryPrefixes);

		// przeliczenie form
		countGrammaFormAndExamples(dictionaryEntryList, indexWriter, addGrammaAndExample, addSugestionList);
		
		dictionaryInputStream.close();
		
		// otwarcie pliku ze zdaniami
		FileInputStream sentencesInputStream = new FileInputStream(sentencesFilePath);
		
		// wczytanie zdan
		readSentenceFile(indexWriter, sentencesInputStream);
		
		sentencesInputStream.close();
		
		// otwarcie pliku z przydzialem zdan do grup
		FileInputStream sentencesGroupsInputStream = new FileInputStream(sentencesGroupsFilePath);
		
		// wczytanie przydzialu zdan do grup
		readSentenceGroupsFile(indexWriter, sentencesGroupsInputStream);
		
		sentencesGroupsInputStream.close();
		
		// otwarcie pliku ze znakami podstawowymi
		FileInputStream radicalInputStream = new FileInputStream(radicalFilePath);

		// wczytywanie pliku ze znakami podstawowymi
		List<RadicalInfo> radicalInfoList = readRadicalEntriesFromCsv(radicalInputStream);

		radicalInputStream.close();

		// otwarcie pliku ze znakami kanji
		FileInputStream kanjiInputStream = new FileInputStream(kanjiFilePath);

		// wczytywanie pliku ze znakami kanji
		readKanjiDictionaryFile(indexWriter, radicalInfoList, kanjiInputStream, addSugestionList, generateKanjiEntryPrefixes);

		kanjiInputStream.close();
		
		// wczytywanie pliku z nazwami
		if (generateNamesDictionary == true) {
			
			File[] namesList = new File(nameFilePath).getParentFile().listFiles(new FileFilter() {
				
				@Override
				public boolean accept(File pathname) {				
					return pathname.getPath().startsWith(nameFilePath);
				}
			});
			
			Arrays.sort(namesList);
			
			Counter idCounter = new Counter(1);
			
			for (File currentName : namesList) {
				
				FileInputStream namesInputStream = new FileInputStream(currentName);
				
				// wczytywanie slownika nazw
				readNamesFile(indexWriter, namesInputStream, idCounter, addSugestionList, generateNameEntryPrefixes);
		
				// zamkniecie pliku
				namesInputStream.close();
			}		
		}	
		
		// dodawanie pliku word2.xml
		if (addWord2Xml == true) {			
			addWord2Xml(indexWriter, jmdict);
		}
				
		// zakonczenie zapisywania indeksu
		indexWriter.close();
		
		// dodatkowo jeszcze, generowanie poprawiacza slow
		generateSpellChecker(dbOutDirFile, analyzer, addSugestionList);
		
		// cache'owanie podpowiadacza slow
		cacheSuggester(dbOutDirFile, analyzer, addSugestionList);
		
		System.out.println("DB Generator - done");
	}

	private static DictionaryEntry parseDictionaryEntry(CsvReader csvReader) throws DictionaryException, IOException {
		
		String idString = csvReader.get(0);
		String dictionaryEntryTypeString = csvReader.get(1);
		String attributesString = csvReader.get(2);
		String groupsString = csvReader.get(4);
		String prefixKanaString = csvReader.get(5);
		String kanjiString = csvReader.get(6);

		String kanaListString = csvReader.get(7);
		String prefixRomajiString = csvReader.get(8);

		String romajiListString = csvReader.get(9);
		String translateListString = csvReader.get(10);
		String infoString = csvReader.get(11);
		
		String exampleSentenceGroupIdsListString = csvReader.get(13);
		
		DictionaryEntry entry = Utils.parseDictionaryEntry(idString, dictionaryEntryTypeString, attributesString,
				groupsString, prefixKanaString, kanjiString, kanaListString, prefixRomajiString, romajiListString,
				translateListString, infoString, exampleSentenceGroupIdsListString);

		return entry;		
	}
	
	private static List<DictionaryEntry> readDictionaryFile(IndexWriter indexWriter, InputStream dictionaryInputStream, JMdict jmdict, boolean addSugestionList, boolean generatePrefixes) throws IOException, DictionaryException, SQLException {
		
		// cachowanie danych w JMdict
		List<Entry> entryList = jmdict.getEntryList();

		Map<Integer, Entry> jmdictCache = new TreeMap<Integer, Entry>();
		
		for (Entry entry : entryList) {
			jmdictCache.put(entry.getEntryId(), entry);
		}
		
		//
		
		List<DictionaryEntry> dictionaryEntryList = new ArrayList<DictionaryEntry>();

		CsvReader csvReader = new CsvReader(new InputStreamReader(dictionaryInputStream), ',');

		Set<GroupEnum> uniqueDictionaryEntryGroupEnumSet = new HashSet<GroupEnum>();

		while (csvReader.readRecord()) {

			DictionaryEntry entry = parseDictionaryEntry(csvReader);
			Entry jmdictEntry = null;
			
			// sprawdzenie, czy wystepuje slowo w formacie JMdict
			List<Attribute> jmdictEntryIdAttributeList = entry.getAttributeList().getAttributeList(AttributeType.JMDICT_ENTRY_ID);
			
			if (jmdictEntryIdAttributeList != null && jmdictEntryIdAttributeList.size() > 0) { // cos jest
				
				// pobieramy entry id
				Integer entryId = Integer.parseInt(jmdictEntryIdAttributeList.get(0).getAttributeValue().get(0));
				
				// pobieramy z cache
				jmdictEntry = jmdictCache.get(entryId);				
			}
			
			System.out.println(String.format("DictionaryEntry id = %s", entry.getId()));

			addDictionaryEntry(indexWriter, entry, jmdictEntry, addSugestionList, generatePrefixes);

			uniqueDictionaryEntryGroupEnumSet.addAll(entry.getGroups());
			
			dictionaryEntryList.add(entry);			
		}

		addDictionaryEntryUniqueGroupEnum(indexWriter, uniqueDictionaryEntryGroupEnumSet);

		csvReader.close();
		
		return dictionaryEntryList;
	}

	private static void addDictionaryEntry(IndexWriter indexWriter, DictionaryEntry dictionaryEntry, Entry jmdictEntry, boolean addSugestionList, boolean generatePrefixes) throws IOException {
		
		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.dictionaryEntry_objectType, Field.Store.YES));
		
		// id
		document.add(new IntField(LuceneStatic.dictionaryEntry_id, dictionaryEntry.getId(), Field.Store.YES));
		
		// dictionary entry type list
		List<String> dictionaryEntryTypeStringList = DictionaryEntryType.convertToValues(dictionaryEntry.getDictionaryEntryTypeList());
		
		for (String dictionaryEntryTypeString : dictionaryEntryTypeStringList) {
			document.add(new StringField(LuceneStatic.dictionaryEntry_dictionaryEntryTypeList, dictionaryEntryTypeString, Field.Store.YES));
		}
				
		// attributeList
		List<String> attributeStringList = dictionaryEntry.getAttributeList().convertAttributeListToListString();
		
		for (String currentAttribute : attributeStringList) {
			document.add(new StringField(LuceneStatic.dictionaryEntry_attributeList, currentAttribute, Field.Store.YES));
		}
		
		// groupsList
		List<String> groupsList = GroupEnum.convertToValues(dictionaryEntry.getGroups());
		
		for (String currentGroup : groupsList) {
			document.add(new StringField(LuceneStatic.dictionaryEntry_groupsList, currentGroup, Field.Store.YES));
		}
		
		// prefixKana
		document.add(new StringField(LuceneStatic.dictionaryEntry_prefixKana, emptyIfNull(dictionaryEntry.getPrefixKana()), Field.Store.YES));
		
		// kanji
		document.add(new StringField(LuceneStatic.dictionaryEntry_kanji, emptyIfNull(dictionaryEntry.getKanji()), Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.dictionaryEntry_kanji, emptyIfNull(dictionaryEntry.getKanji()), generatePrefixes);
		
		if (addSugestionList == true) {
			
			addSuggestion(document, LuceneStatic.dictionaryEntry_android_sugestionList, emptyIfNull(dictionaryEntry.getKanji()), false);
			addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(dictionaryEntry.getKanji()), false);
			
			addSpellChecker(document, LuceneStatic.dictionaryEntry_android_spellCheckerList, emptyIfNull(dictionaryEntry.getKanji()));
			addSpellChecker(document, LuceneStatic.dictionaryEntry_web_spellCheckerList, emptyIfNull(dictionaryEntry.getKanji()));
		}
		
		// kanaList
		String kana = dictionaryEntry.getKana();
		
		document.add(new StringField(LuceneStatic.dictionaryEntry_kana, kana, Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.dictionaryEntry_kana, kana, generatePrefixes);
		
		if (addSugestionList == true) {
			
			addSuggestion(document, LuceneStatic.dictionaryEntry_android_sugestionList, kana, false);
			addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, kana, false);
			
			addSpellChecker(document, LuceneStatic.dictionaryEntry_android_spellCheckerList, kana);
			addSpellChecker(document, LuceneStatic.dictionaryEntry_web_spellCheckerList, kana);
		}
		
		// prefixRomaji
		document.add(new StringField(LuceneStatic.dictionaryEntry_prefixRomaji, emptyIfNull(dictionaryEntry.getPrefixRomaji()), Field.Store.YES));
		
		// romajiList
		String romaji = dictionaryEntry.getRomaji();
				
		document.add(new TextField(LuceneStatic.dictionaryEntry_romaji, romaji, Field.Store.YES));

		addPrefixes(document, LuceneStatic.dictionaryEntry_romaji, romaji, generatePrefixes);
		
		//
		
		// dodanie alternatyw romaji
		addAlternativeRomaji(document, LuceneStatic.dictionaryEntry_virtual_romaji, romaji, generatePrefixes);
		
		if (addSugestionList == true) {
			
			addSuggestion(document, LuceneStatic.dictionaryEntry_android_sugestionList, romaji, false);
			addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, romaji, false);
			
			addSpellChecker(document, LuceneStatic.dictionaryEntry_android_spellCheckerList, romaji);
			addSpellChecker(document, LuceneStatic.dictionaryEntry_web_spellCheckerList, romaji);			
		}
		
		// translatesList
		List<String> translates = dictionaryEntry.getTranslates();
		
		for (String currentTranslate : translates) {
			
			document.add(new TextField(LuceneStatic.dictionaryEntry_translatesList, currentTranslate, Field.Store.YES));
			
			addPrefixes(document, LuceneStatic.dictionaryEntry_translatesList, currentTranslate, generatePrefixes);
			
			if (addSugestionList == true) {
								
				addSuggestion(document, LuceneStatic.dictionaryEntry_android_sugestionList, currentTranslate, true);
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, currentTranslate, true);
				
				addSpellChecker(document, LuceneStatic.dictionaryEntry_android_spellCheckerList, currentTranslate);
				addSpellChecker(document, LuceneStatic.dictionaryEntry_web_spellCheckerList, currentTranslate);
			}
			
			String currentTranslateWithoutPolishChars = Utils.removePolishChars(currentTranslate);
				
			document.add(new TextField(LuceneStatic.dictionaryEntry_translatesListWithoutPolishChars, currentTranslateWithoutPolishChars, Field.Store.NO));
			
			addPrefixes(document, LuceneStatic.dictionaryEntry_translatesListWithoutPolishChars, currentTranslateWithoutPolishChars, generatePrefixes);
		}
		
		// info
		String info = emptyIfNull(dictionaryEntry.getInfo());
		
		document.add(new TextField(LuceneStatic.dictionaryEntry_info, info, Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.dictionaryEntry_info, info, generatePrefixes);
			
		String infoWithoutPolishChars = Utils.removePolishChars(info);
			
		document.add(new TextField(LuceneStatic.dictionaryEntry_infoWithoutPolishChars, infoWithoutPolishChars, Field.Store.NO));
		
		addPrefixes(document, LuceneStatic.dictionaryEntry_infoWithoutPolishChars, infoWithoutPolishChars, generatePrefixes);
		
		// example sentence groupIds
		List<String> exampleSentenceGroupIdsList = dictionaryEntry.getExampleSentenceGroupIdsList();
				
		for (String currentExampleSenteceGroupId : exampleSentenceGroupIdsList) {
			document.add(new TextField(LuceneStatic.dictionaryEntry_exampleSentenceGroupIdsList, currentExampleSenteceGroupId, Field.Store.YES));
		}
		
		// generowanie tlumaczen slowa w formacie wyswietlanym podczas wyszukiwania
		if (jmdictEntry != null) { // slowo w nowym formacie
			
			List<KanjiKanaPair> kanjiKanaPairList = Dictionary2HelperCommon.getKanjiKanaPairListStatic(jmdictEntry);
			
			// szukamy konkretnego znaczenia dla naszego slowa
			KanjiKanaPair dictionaryEntry2KanjiKanaPair = Dictionary2HelperCommon.findKanjiKanaPair(kanjiKanaPairList, dictionaryEntry);

			if (dictionaryEntry2KanjiKanaPair != null) {				
				for (int senseIdx = 0; senseIdx < dictionaryEntry2KanjiKanaPair.getSenseList().size(); ++senseIdx) {
					
					Sense sense = dictionaryEntry2KanjiKanaPair.getSenseList().get(senseIdx);
					
					List<Gloss> glossList = sense.getGlossList();
					List<SenseAdditionalInfo> senseAdditionalInfoList = sense.getAdditionalInfoList();
					List<LanguageSource> senseLanguageSourceList = sense.getLanguageSourceList();
					List<FieldEnum> senseFieldList = sense.getFieldList();
					List<MiscEnum> senseMiscList = sense.getMiscList();
					List<DialectEnum> senseDialectList = sense.getDialectList();
					List<PartOfSpeechEnum> partOfSpeechList = sense.getPartOfSpeechList();
														
					// pobieramy polskie tlumaczenia
					List<Gloss> glossPolList = glossList.stream().filter(gloss -> (gloss.getLang().equals("pol") == true)).collect(Collectors.toList());
					
					// i informacje dodatkowe
					Optional<SenseAdditionalInfo> senseAdditionalPolOptional = senseAdditionalInfoList.stream().filter(additionalInfo -> (additionalInfo.getLang().equals("pol") == true)).findFirst();				
					
					String partOfSpeechValue = null;
					
					// czesci mowy
					if (partOfSpeechList.size() > 0) {						
						List<String> translateToPolishPartOfSpeechEnum = Dictionary2HelperCommon.translateToPolishPartOfSpeechEnum(partOfSpeechList);
						
						partOfSpeechValue = pl.idedyk.japanese.dictionary.api.dictionary.Utils.convertListToString(translateToPolishPartOfSpeechEnum, "; ");
					}				
					
					for (Gloss currentGlossPol : glossPolList) {
						
						Tr tr = new Tr();

						// dodanie pojedynczego znaczenia
						Td glossPolValueTd = new Td();
						
						// wyroznienie znaczenia
						H glossPolTdH4 = new H(4, null, "margin-top: 0px;margin-bottom: 5px");
						
						glossPolTdH4.addHtmlElement(new Text(currentGlossPol.getValue()));
						
						glossPolValueTd.addHtmlElement(glossPolTdH4);
												
						tr.addHtmlElement(glossPolValueTd);
						
						// sprawdzenie, czy wystepuje dodatkowy typ znaczenia
						if (currentGlossPol.getGType() != null) {
							
							Td glossPolGTypeTd = new Td();
							
							Div glossPolGTypeTdDiv = new Div(null, "margin-top: 0px;margin-left: 25px;margin-bottom: 5px");
							
							glossPolGTypeTdDiv.addHtmlElement(new Text(Dictionary2HelperCommon.translateToPolishGlossType(currentGlossPol.getGType())));
							
							glossPolGTypeTd.addHtmlElement(glossPolGTypeTdDiv);
							
							tr.addHtmlElement(glossPolGTypeTd);
						}
							
											
						table.addHtmlElement(tr);					
					}
					
					// informacje dodatkowe
					List<String> additionalInfoToAddList = new ArrayList<>();
					
					// dziedzina
					if (senseFieldList.size() > 0) {
						additionalInfoToAddList.addAll(Dictionary2HelperCommon.translateToPolishFieldEnumList(senseFieldList));						
					}
					
					// rozne informacje
					if (senseMiscList.size() > 0) {
						additionalInfoToAddList.addAll(Dictionary2HelperCommon.translateToPolishMiscEnumList(senseMiscList));
					}
					
					// dialekt
					if (senseDialectList.size() > 0) {
						additionalInfoToAddList.addAll(Dictionary2HelperCommon.translateToPolishDialectEnumList(senseDialectList));
					}
					
					if (senseAdditionalPolOptional.isPresent() == true) { // czy informacje dodatkowe istnieja
						
						String senseAdditionalPolOptionalValue = senseAdditionalPolOptional.get().getValue();
						
						additionalInfoToAddList.add(senseAdditionalPolOptionalValue);
					}
					
					// czy sa informacje o zagranicznym pochodzeniu slow
					if (senseLanguageSourceList != null && senseLanguageSourceList.size() > 0) {
						
						for (LanguageSource languageSource : senseLanguageSourceList) {
													
							String languageCodeInPolish = Dictionary2HelperCommon.translateToPolishLanguageCode(languageSource.getLang());
							String languageValue = languageSource.getValue();
							String languageLsWasei = Dictionary2HelperCommon.translateToPolishLanguageSourceLsWaseiEnum(languageSource.getLsWasei());
							
							if (StringUtils.isBlank(languageValue) == false) {
								additionalInfoToAddList.add(languageCodeInPolish + ": " + languageValue);
								
							} else {
								additionalInfoToAddList.add(Dictionary2HelperCommon.translateToPolishLanguageCodeWithoutValue(languageSource.getLang()));
							}
							
							if (languageLsWasei != null) {
								additionalInfoToAddList.add(languageLsWasei);
							}
						}
					}
									
					if (additionalInfoToAddList.size() > 0) {
						
						Tr tr = new Tr();
						
						Td senseAdditionalPolTd = new Td();
						
						senseAdditionalPolTd.addHtmlElement(new Text(pl.idedyk.japanese.dictionary.api.dictionary.Utils.convertListToString(additionalInfoToAddList, "; ")));
						
						tr.addHtmlElement(senseAdditionalPolTd);
						
						table.addHtmlElement(tr);
					}
				}
			}
		}
		
		indexWriter.addDocument(document);
	}
	
	private static void addAlternativeRomaji(Document document, String fieldName, String romaji, boolean generatePrefixes) {
		
		if (romaji.contains(" ") == true) {
			
			String romajiWithoutSpace = romaji.replaceAll(" ", "");
			
			document.add(new TextField(fieldName, romajiWithoutSpace, Field.Store.NO));
			
			addPrefixes(document, fieldName, romajiWithoutSpace, generatePrefixes);
		}
		
		if (romaji.contains("'") == true) {
			
			String romajiWithoutChar = romaji.replaceAll("'", "");
			
			document.add(new TextField(fieldName, romajiWithoutChar, Field.Store.NO));	
			
			addPrefixes(document, fieldName, romajiWithoutChar, generatePrefixes);
		}		
		
		if (romaji.contains("du") == true) {
			
			String romajiDzu = romaji.replaceAll("du", "dzu");
			
			document.add(new TextField(fieldName, romajiDzu, Field.Store.NO));
			
			addPrefixes(document, fieldName, romajiDzu, generatePrefixes);
		}
		
		if (romaji.contains(" o ") == true) {
			
			String romajiWo = romaji.replaceAll(" o ", " wo ");
						
			document.add(new TextField(fieldName, romajiWo, Field.Store.NO));
			
			if (generatePrefixes == true) {
				addPrefixes(document, fieldName, romajiWo, generatePrefixes);
			}
		}

		if (romaji.contains("tsu") == true) {
			
			String romajiTu = romaji.replaceAll("tsu", "tu");
			
			document.add(new TextField(fieldName, romajiTu, Field.Store.NO));
			
			addPrefixes(document, fieldName, romajiTu, generatePrefixes);
		}

		if (romaji.contains("shi") == true) {
			
			String romajiSi = romaji.replaceAll("shi", "si");
						
			document.add(new TextField(fieldName, romajiSi, Field.Store.NO));
			
			addPrefixes(document, fieldName, romajiSi, generatePrefixes);
		}

		if (romaji.contains("fu") == true) {
			
			String romajiHu = romaji.replaceAll("fu", "hu");
						
			document.add(new TextField(fieldName, romajiHu, Field.Store.NO));
			
			addPrefixes(document, fieldName, romajiHu, generatePrefixes);
		}		
	}
	
	private static void addPrefixes(Document document, String fieldName, String value, boolean generatePrefixes) {
		
		if (generatePrefixes == false) {
			return;
		}
		
		for (int idx = 0; idx < value.length(); ++idx) {
			
			String prefix = value.substring(idx);
			
			document.add(new TextField(fieldName + "_" + LuceneStatic.prefix, prefix, Field.Store.NO));			
		}
	}
	
	private static void countGrammaFormAndExamples(List<DictionaryEntry> dictionaryEntryList, IndexWriter indexWriter, boolean addGrammaAndExample, boolean addSugestionList) throws IOException {
		
		KeigoHelper keigoHelper = new KeigoHelper();
		
		for (DictionaryEntry dictionaryEntry : dictionaryEntryList) {
			
			System.out.println(String.format("DictionaryEntry(countGrammaFormAndExamples) id = %s", dictionaryEntry.getId()));
			
			// count form for dictionary entry
			countGrammaFormAndExamples(indexWriter, dictionaryEntry, keigoHelper, addGrammaAndExample, addSugestionList);	
		}
	}
	
	private static void countGrammaFormAndExamples(IndexWriter indexWriter, DictionaryEntry dictionaryEntry, KeigoHelper keigoHelper, boolean addGrammaAndExample, boolean addSugestionList) throws IOException {
		
		if (addGrammaAndExample == false) {
			
			// count form for dictionary entry
			Map<GrammaFormConjugateResultType, GrammaFormConjugateResult> grammaFormCache = new HashMap<GrammaFormConjugateResultType, GrammaFormConjugateResult>();
			
			GrammaConjugaterManager.getGrammaConjufateResult(keigoHelper, dictionaryEntry, grammaFormCache, null, true);
			
			for (DictionaryEntryType currentDictionaryEntryType : dictionaryEntry.getDictionaryEntryTypeList()) {
				GrammaConjugaterManager.getGrammaConjufateResult(keigoHelper, dictionaryEntry, grammaFormCache, currentDictionaryEntryType, true);
			}
			
			ExampleManager.getExamples(keigoHelper, dictionaryEntry, grammaFormCache, null, true);
			
			for (DictionaryEntryType currentDictionaryEntryType : dictionaryEntry.getDictionaryEntryTypeList()) {
				ExampleManager.getExamples(keigoHelper, dictionaryEntry, grammaFormCache, currentDictionaryEntryType, true);
			}
			
		} else {
			
			// create document
			Document grammaAndExampleDocument = new Document();
			
			// object type
			grammaAndExampleDocument.add(new StringField(LuceneStatic.objectType, LuceneStatic.dictionaryEntry_grammaConjufateResult_and_exampleResult_objectType, Field.Store.YES));
			
			// id
			grammaAndExampleDocument.add(new IntField(LuceneStatic.dictionaryEntry_grammaConjufateResult_and_exampleResult_dictionaryEntry_id, dictionaryEntry.getId(), Field.Store.YES));

			// count gramma
			Map<GrammaFormConjugateResultType, GrammaFormConjugateResult> grammaFormCache = new HashMap<GrammaFormConjugateResultType, GrammaFormConjugateResult>();

			GrammaConjugaterManager.getGrammaConjufateResult(keigoHelper, dictionaryEntry, grammaFormCache, null, true);
			
			for (DictionaryEntryType currentDictionaryEntryType : dictionaryEntry.getDictionaryEntryTypeList()) {
				
				List<GrammaFormConjugateGroupTypeElements> grammaConjufateResult = GrammaConjugaterManager.getGrammaConjufateResult(keigoHelper, dictionaryEntry, grammaFormCache,
						currentDictionaryEntryType, true);
				
				addGrammaFormConjugateGroupList(grammaAndExampleDocument, indexWriter, dictionaryEntry, grammaConjufateResult, addSugestionList);
			}

			List<ExampleGroupTypeElements> examples = ExampleManager.getExamples(keigoHelper, dictionaryEntry, grammaFormCache, null, true);
			
						
			for (DictionaryEntryType currentDictionaryEntryType : dictionaryEntry.getDictionaryEntryTypeList()) {
				examples = ExampleManager.getExamples(keigoHelper, dictionaryEntry, grammaFormCache, currentDictionaryEntryType, true);
				
				addExampleGroupTypeList(grammaAndExampleDocument, indexWriter, dictionaryEntry, examples, addSugestionList);
			}
			
			if (grammaAndExampleDocument.getFields().size() > 2) {
				indexWriter.addDocument(grammaAndExampleDocument);
			}
		}		
	}
	
	private static void addGrammaFormConjugateGroupList(Document document, IndexWriter indexWriter, DictionaryEntry dictionaryEntry, 
			List<GrammaFormConjugateGroupTypeElements> grammaConjufateResult, boolean addSugestionList) throws IOException {
		
		if (grammaConjufateResult == null) {
			return;
		}
		
		for (GrammaFormConjugateGroupTypeElements grammaFormConjugateGroupTypeElements : grammaConjufateResult) {
						
			List<GrammaFormConjugateResult> grammaFormConjugateResults = grammaFormConjugateGroupTypeElements.getGrammaFormConjugateResults();
			
			for (GrammaFormConjugateResult grammaFormConjugateResult : grammaFormConjugateResults) {				
				addGrammaFormConjugateResult(document, indexWriter, dictionaryEntry, grammaFormConjugateResult, addSugestionList);
			}
		}		
	}
	
	private static void addExampleGroupTypeList(Document document, IndexWriter indexWriter, DictionaryEntry dictionaryEntry, 
			List<ExampleGroupTypeElements> exampleGroupTypeElementsList, boolean addSugestionList) throws IOException {
		
		if (exampleGroupTypeElementsList == null) {
			return;
		}
		
		for (ExampleGroupTypeElements exampleGroupTypeElements : exampleGroupTypeElementsList) {
			
			List<ExampleResult> exampleResults = exampleGroupTypeElements.getExampleResults();
			
			for (ExampleResult currentExampleResult : exampleResults) {
				addExampleResult(document, indexWriter, dictionaryEntry, currentExampleResult, addSugestionList);
			}
		}		
	}
	
	private static void addGrammaFormConjugateResult(Document document, IndexWriter indexWriter, DictionaryEntry dictionaryEntry, 
			GrammaFormConjugateResult grammaFormConjugateResult, boolean addSugestionList) throws IOException {
				
		// kanji
		if (grammaFormConjugateResult.isKanjiExists() == true) {					
			document.add(new StringField(LuceneStatic.dictionaryEntry_grammaConjufateResult_and_exampleResult_kanji, grammaFormConjugateResult.getKanji(), Field.Store.YES));
			
			if (addSugestionList == true) {
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(grammaFormConjugateResult.getKanji()), false);
			}
		}
				
		// kanaList
		List<String> kanaList = grammaFormConjugateResult.getKanaList();
		
		for (String currentKana : kanaList) {
			document.add(new StringField(LuceneStatic.dictionaryEntry_grammaConjufateResult_and_exampleResult_kanaList, currentKana, Field.Store.YES));
			
			if (addSugestionList == true) {
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(currentKana), false);
			}
		}
		
		// romajiList 
		List<String> romajiList = grammaFormConjugateResult.getRomajiList();
		
		for (String currentRomaji : romajiList) {
			document.add(new TextField(LuceneStatic.dictionaryEntry_grammaConjufateResult_and_exampleResult_romajiList, currentRomaji, Field.Store.YES));
			
			// dodanie alternatyw romaji
			addAlternativeRomaji(document, LuceneStatic.dictionaryEntry_grammaConjufateResult_and_exampleResult_virtual_romajiList, currentRomaji, false);
						
			if (addSugestionList == true) {
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(currentRomaji), false);
			}
		}
		
		if (grammaFormConjugateResult.getAlternative() != null) {
			addGrammaFormConjugateResult(document, indexWriter, dictionaryEntry, grammaFormConjugateResult.getAlternative(), addSugestionList);
		}
	}
	
	private static void addExampleResult(Document document, IndexWriter indexWriter, DictionaryEntry dictionaryEntry, ExampleResult exampleResult,
			boolean addSugestionList) throws IOException {
		
		// kanji
		if (exampleResult.isKanjiExists() == true) {					
			document.add(new StringField(LuceneStatic.dictionaryEntry_grammaConjufateResult_and_exampleResult_kanji, exampleResult.getKanji(), Field.Store.YES));
			
			if (addSugestionList == true) {
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(exampleResult.getKanji()), false);
			}
		}
		
		// kanaList
		List<String> kanaList = exampleResult.getKanaList();
		
		for (String currentKana : kanaList) {
			document.add(new StringField(LuceneStatic.dictionaryEntry_grammaConjufateResult_and_exampleResult_kanaList, currentKana, Field.Store.YES));
			
			if (addSugestionList == true) {
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(currentKana), false);
			}
		}
		
		// romajiList 
		List<String> romajiList = exampleResult.getRomajiList();
		
		for (String currentRomaji : romajiList) {
			document.add(new TextField(LuceneStatic.dictionaryEntry_grammaConjufateResult_and_exampleResult_romajiList, currentRomaji, Field.Store.YES));
			
			// dodanie alternatyw romaji
			addAlternativeRomaji(document, LuceneStatic.dictionaryEntry_grammaConjufateResult_and_exampleResult_virtual_romajiList, currentRomaji, false);
			
			if (addSugestionList == true) {
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(currentRomaji), false);
			}
		}
		
		if (exampleResult.getAlternative() != null) {
			addExampleResult(document, indexWriter, dictionaryEntry, exampleResult.getAlternative(), addSugestionList);
		}		
	}
	
	private static void readSentenceFile(IndexWriter indexWriter, FileInputStream sentencesInputStream) throws IOException {
		
		CsvReader csvReader = new CsvReader(new InputStreamReader(sentencesInputStream), ',');
		
		while (csvReader.readRecord()) {
			
			String id = csvReader.get(0);
			String lang = csvReader.get(1);
			String sentence = csvReader.get(2);
			
			System.out.println(String.format("TatoebaSentence id = %s", id));
			
			TatoebaSentence tatoebaSentence = new TatoebaSentence();
			
			tatoebaSentence.setId(id);
			tatoebaSentence.setLang(lang);
			tatoebaSentence.setSentence(sentence);
			
			addTatoebaSentence(indexWriter, tatoebaSentence);
		}		
		
		csvReader.close();
	}

	private static void addTatoebaSentence(IndexWriter indexWriter, TatoebaSentence tatoebaSentence) throws IOException {
		
		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.dictionaryEntry_exampleSentence_objectType, Field.Store.YES));
		
		// id
		document.add(new StringField(LuceneStatic.dictionaryEntry_exampleSentence_id, tatoebaSentence.getId(), Field.Store.YES));

		// lang
		document.add(new StringField(LuceneStatic.dictionaryEntry_exampleSentence_lang, tatoebaSentence.getLang(), Field.Store.YES));
		
		// sentence
		document.add(new StringField(LuceneStatic.dictionaryEntry_exampleSentence_sentence, tatoebaSentence.getSentence(), Field.Store.YES));
		
		indexWriter.addDocument(document);
	}

	private static void addDictionaryEntryUniqueGroupEnum(IndexWriter indexWriter, Set<GroupEnum> uniqueDictionaryEntryGroupEnumSet) throws IOException {
		
		List<GroupEnum> uniqueDictionaryEntryGroupEnumList = GroupEnum.sortGroups(new ArrayList<GroupEnum>(uniqueDictionaryEntryGroupEnumSet));
				
		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.uniqueDictionaryEntryGroupEnumList_objectType, Field.Store.YES));

		for (GroupEnum groupEnum : uniqueDictionaryEntryGroupEnumList) {
			document.add(new StringField(LuceneStatic.uniqueDictionaryEntryGroupEnumList_groupsList, groupEnum.getValue(), Field.Store.YES));
		}		
		
		indexWriter.addDocument(document);
	}	
	
	private static void readSentenceGroupsFile(IndexWriter indexWriter, FileInputStream sentencesGroupsInputStream) throws IOException {
		
		CsvReader csvReader = new CsvReader(new InputStreamReader(sentencesGroupsInputStream), ',');
		
		while (csvReader.readRecord()) {
			
			String groupId = csvReader.get(0);
			String sentenceIdListString = csvReader.get(1);
			
			List<String> sentenceIdList = Utils.parseStringIntoList(sentenceIdListString /*, false */);
			
			GroupWithTatoebaSentenceList groupWithTatoebaSentenceList = new GroupWithTatoebaSentenceList();

			List<TatoebaSentence> tatoebaSentenceLazyList = new ArrayList<TatoebaSentence>();
			
			for (String currentSentenceId : sentenceIdList) {
				TatoebaSentence tatoebaSentence = new TatoebaSentence();
				
				tatoebaSentence.setId(currentSentenceId);
				
				tatoebaSentenceLazyList.add(tatoebaSentence);
			}
			
			groupWithTatoebaSentenceList.setGroupId(groupId);
			groupWithTatoebaSentenceList.setTatoebaSentenceList(tatoebaSentenceLazyList);
			
			addGroupWithTatoebaSentenceList(indexWriter, groupWithTatoebaSentenceList);
		}		
		
		csvReader.close();	
	}
	
	private static void addGroupWithTatoebaSentenceList(IndexWriter indexWriter, GroupWithTatoebaSentenceList groupWithTatoebaSentenceList) throws IOException {
		
		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.dictionaryEntry_exampleSentenceGroups_objectType, Field.Store.YES));
		
		// id
		document.add(new StringField(LuceneStatic.dictionaryEntry_exampleSentenceGroups_groupId, groupWithTatoebaSentenceList.getGroupId(), Field.Store.YES));

		// lang
		List<TatoebaSentence> tatoebaSentenceList = groupWithTatoebaSentenceList.getTatoebaSentenceList();
		
		for (TatoebaSentence currentTatoebaSentence : tatoebaSentenceList) {
			document.add(new StringField(LuceneStatic.dictionaryEntry_exampleSentenceGroups_sentenceIdList, currentTatoebaSentence.getId(), Field.Store.YES));
		}
				
		indexWriter.addDocument(document);
	}
	
	private static List<RadicalInfo> readRadicalEntriesFromCsv(InputStream radicalInputStream) throws IOException,
			DictionaryException {

		List<RadicalInfo> radicalList = new ArrayList<RadicalInfo>();

		CsvReader csvReader = new CsvReader(new InputStreamReader(radicalInputStream), ',');

		while (csvReader.readRecord()) {

			int id = Integer.parseInt(csvReader.get(0));

			String radical = csvReader.get(1);

			if (radical.equals("") == true) {
				throw new DictionaryException("Empty radical: " + radical);
			}

			String strokeCountString = csvReader.get(2);

			int strokeCount = Integer.parseInt(strokeCountString);

			RadicalInfo entry = new RadicalInfo();

			entry.setId(id);
			entry.setRadical(radical);
			entry.setStrokeCount(strokeCount);

			radicalList.add(entry);
		}

		csvReader.close();

		return radicalList;
	}

	private static void readKanjiDictionaryFile(IndexWriter indexWriter, List<RadicalInfo> radicalInfoList,
			InputStream kanjiInputStream, boolean addSugestionList, boolean generatePrefixes) throws IOException, DictionaryException, SQLException {

		Map<String, RadicalInfo> radicalListMapCache = new HashMap<String, RadicalInfo>();

		for (RadicalInfo currentRadicalInfo : radicalInfoList) {

			String radical = currentRadicalInfo.getRadical();

			radicalListMapCache.put(radical, currentRadicalInfo);
		}
		
		Set<String> allAvailableRadicalSet = new HashSet<String>();

		CsvReader csvReader = new CsvReader(new InputStreamReader(kanjiInputStream), ',');

		while (csvReader.readRecord()) {

			String idString = csvReader.get(0);

			String kanjiString = csvReader.get(1);

			String strokeCountString = csvReader.get(2);

			String radicalsString = csvReader.get(3);

			String onReadingString = csvReader.get(4);

			String kunReadingString = csvReader.get(5);

			String strokePathString = csvReader.get(6);

			String polishTranslateListString = csvReader.get(7);
			String infoString = csvReader.get(8);

			String usedString = csvReader.get(9);

			String groupString = csvReader.get(10);

			KanjiEntry entry = Utils.parseKanjiEntry(idString, kanjiString, strokeCountString,
					Utils.parseStringIntoList(radicalsString /*, false */),
					Utils.parseStringIntoList(onReadingString /*, false */),
					Utils.parseStringIntoList(kunReadingString /*, false */), strokePathString,
					Utils.parseStringIntoList(polishTranslateListString /*, false */), infoString, usedString,
					Utils.parseStringIntoList(groupString /*, false */));

			System.out.println(String.format("KanjiEntry id = %s", entry.getId()));
			
			// update radical info
			if (entry.getKanjiDic2Entry() != null) {
				//updateRadicalInfoUse(radicalListMapCache, entry.getKanjiDic2Entry().getRadicals());
				
				allAvailableRadicalSet.addAll(entry.getKanjiDic2Entry().getRadicals());
			}

			// add
			addKanjiEntry(indexWriter, entry, addSugestionList, generatePrefixes);
		}
		
		// add available radical list
		addAvailableRadicalList(indexWriter, allAvailableRadicalSet);

		csvReader.close();
	}
	
	/*
	private static void updateRadicalInfoUse(Map<String, RadicalInfo> radicalListMapCache, List<String> radicals) {

		for (String currentRadical : radicals) {

			RadicalInfo currentRadicalInfo = radicalListMapCache.get(currentRadical);

			if (currentRadicalInfo == null) {
				throw new RuntimeException("currentRadicalInfo == null: " + currentRadical);
			}

			//currentRadicalInfo.incrementUse();			
		}
	}
	*/

	public static void addKanjiEntry(IndexWriter indexWriter, KanjiEntry kanjiEntry, boolean addSugestionList, boolean generatePrefixes) throws IOException {

		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.kanjiEntry_objectType, Field.Store.YES));
		
		// id
		document.add(new IntField(LuceneStatic.kanjiEntry_id, kanjiEntry.getId(), Field.Store.YES));

		// kanji
		document.add(new StringField(LuceneStatic.kanjiEntry_kanji, emptyIfNull(kanjiEntry.getKanji()), Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.kanjiEntry_kanji, emptyIfNull(kanjiEntry.getKanji()), generatePrefixes);
		
		if (addSugestionList == true) {
			
			addSuggestion(document, LuceneStatic.kanjiEntry_android_sugestionList, emptyIfNull(kanjiEntry.getKanji()), false);
			addSuggestion(document, LuceneStatic.kanjiEntry_web_sugestionList, emptyIfNull(kanjiEntry.getKanji()), false);
			
			addSpellChecker(document, LuceneStatic.kanjiEntry_android_spellCheckerList, emptyIfNull(kanjiEntry.getKanji()));
			addSpellChecker(document, LuceneStatic.kanjiEntry_web_spellCheckerList, emptyIfNull(kanjiEntry.getKanji()));
		}
			
		// polishTranslatesList
		List<String> polishtranslatesList = kanjiEntry.getPolishTranslates();
		
		for (String currentTranslate : polishtranslatesList) {
			
			document.add(new TextField(LuceneStatic.kanjiEntry_polishTranslatesList, currentTranslate, Field.Store.YES));
			
			addPrefixes(document, LuceneStatic.kanjiEntry_polishTranslatesList, currentTranslate, generatePrefixes);
			
			if (addSugestionList == true) {
								
				addSuggestion(document, LuceneStatic.kanjiEntry_android_sugestionList, currentTranslate, true);
				addSuggestion(document, LuceneStatic.kanjiEntry_web_sugestionList, currentTranslate, true);
				
				addSpellChecker(document, LuceneStatic.kanjiEntry_android_spellCheckerList, currentTranslate);
				addSpellChecker(document, LuceneStatic.kanjiEntry_web_spellCheckerList, currentTranslate);				
			}
			
			String currentTranslateWithoutPolishChars = Utils.removePolishChars(currentTranslate);
				
			document.add(new TextField(LuceneStatic.kanjiEntry_infoWithoutPolishChars, currentTranslateWithoutPolishChars, Field.Store.NO));
			
			addPrefixes(document, LuceneStatic.kanjiEntry_infoWithoutPolishChars, currentTranslateWithoutPolishChars, generatePrefixes);
		}
		
		// info
		String info = emptyIfNull(kanjiEntry.getInfo());
		
		document.add(new TextField(LuceneStatic.kanjiEntry_info, info, Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.kanjiEntry_info, info, generatePrefixes);
		
		String infoWithoutPolishChars = Utils.removePolishChars(info);
			
		document.add(new TextField(LuceneStatic.kanjiEntry_infoWithoutPolishChars, infoWithoutPolishChars, Field.Store.NO));
		
		addPrefixes(document, LuceneStatic.kanjiEntry_infoWithoutPolishChars, infoWithoutPolishChars, generatePrefixes);

		// generated
		document.add(new StringField(LuceneStatic.kanjiEntry_used, String.valueOf(kanjiEntry.isUsed()), Field.Store.YES));
				
		// groupsList
		List<String> groupsList = GroupEnum.convertToValues(kanjiEntry.getGroups());
		
		for (String currentGroup : groupsList) {
			document.add(new StringField(LuceneStatic.kanjiEntry_groupsList, currentGroup, Field.Store.YES));
		}
		
		KanjiDic2Entry kanjiDic2Entry = kanjiEntry.getKanjiDic2Entry();

		if (kanjiDic2Entry != null) {

			// kanjiDic2Entry_strokeCount
			document.add(new IntField(LuceneStatic.kanjiEntry_kanjiDic2Entry_strokeCount, kanjiDic2Entry.getStrokeCount(), Field.Store.YES));
			
			// kanjiDic2Entry_onReadingList
			List<String> onReadingList = kanjiDic2Entry.getOnReading();
			
			for (String currentOnReading : onReadingList) {
				document.add(new StringField(LuceneStatic.kanjiEntry_kanjiDic2Entry_onReadingList, currentOnReading, Field.Store.YES));
			}

			// kanjiDic2Entry_kunReadingList
			List<String> kunReadingList = kanjiDic2Entry.getKunReading();
			
			for (String currentKunReading : kunReadingList) {
				document.add(new StringField(LuceneStatic.kanjiEntry_kanjiDic2Entry_kunReadingList, currentKunReading, Field.Store.YES));
			}
			
			// kanjiDic2Entry_radicalsList
			List<String> radicalsList = kanjiDic2Entry.getRadicals();
			
			for (String currentRadical : radicalsList) {
				document.add(new StringField(LuceneStatic.kanjiEntry_kanjiDic2Entry_radicalsList, currentRadical, Field.Store.YES));
			}
			
			// kanjiDic2Entry_jlpt
			Integer jlpt = kanjiDic2Entry.getJlpt();
			
			if (jlpt != null) {
				document.add(new IntField(LuceneStatic.kanjiEntry_kanjiDic2Entry_jlpt, jlpt, Field.Store.YES));
			}			
			
			// kanjiDic2Entry_freq
			Integer freq = kanjiDic2Entry.getFreq();
			
			if (freq != null) {
				document.add(new IntField(LuceneStatic.kanjiEntry_kanjiDic2Entry_freq, freq, Field.Store.YES));
			}
		}
		
		KanjivgEntry kanjivgEntry = kanjiEntry.getKanjivgEntry();
		
		if (kanjivgEntry != null) {
			
			// kanjivgEntry_strokePaths
			List<String> strokePaths = kanjivgEntry.getStrokePaths();
						
			for (String currentStrokePath : strokePaths) {
				document.add(new StringField(LuceneStatic.kanjiEntry_kanjivgEntry_strokePaths, currentStrokePath, Field.Store.YES));
			}
		}
		

		indexWriter.addDocument(document);		
	}
	
	private static List<DictionaryEntry> readNamesFile(IndexWriter indexWriter, InputStream namesInputStream, Counter idCounter, boolean addSugestionList, boolean generatePrefixes) throws IOException, DictionaryException, SQLException {
		
		List<DictionaryEntry> namesDictionaryEntryList = new ArrayList<DictionaryEntry>();

		CsvReader csvReader = new CsvReader(new InputStreamReader(namesInputStream), ',');

		while (csvReader.readRecord()) {

			DictionaryEntry entry = parseDictionaryEntry(csvReader);
			
			entry.setId(idCounter.getValue());
			
			idCounter.increment();
			
			System.out.println(String.format("DictionaryEntry (name) id = %s", entry.getId()));

			addNameDictionaryEntry(indexWriter, entry, addSugestionList, generatePrefixes);
			
			namesDictionaryEntryList.add(entry);			
		}

		csvReader.close();
		
		return namesDictionaryEntryList;
	}
	
	private static void addNameDictionaryEntry(IndexWriter indexWriter, DictionaryEntry dictionaryEntry, boolean addSugestionList, boolean generatePrefixes) throws IOException {
		
		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.nameDictionaryEntry_objectType, Field.Store.YES));
		
		// id
		document.add(new IntField(LuceneStatic.nameDictionaryEntry_id, dictionaryEntry.getId(), Field.Store.YES));
		
		// dictionary entry type list
		List<String> dictionaryEntryTypeStringList = DictionaryEntryType.convertToValues(dictionaryEntry.getDictionaryEntryTypeList());
		
		for (String dictionaryEntryTypeString : dictionaryEntryTypeStringList) {
			document.add(new StringField(LuceneStatic.nameDictionaryEntry_dictionaryEntryTypeList, dictionaryEntryTypeString, Field.Store.YES));
		}
				
		// kanji
		document.add(new StringField(LuceneStatic.nameDictionaryEntry_kanji, emptyIfNull(dictionaryEntry.getKanji()), Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.nameDictionaryEntry_kanji, emptyIfNull(dictionaryEntry.getKanji()), generatePrefixes);
		
		if (addSugestionList == true) {
			addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(dictionaryEntry.getKanji()), false);
		}
		
		// kanaList
		String kana = dictionaryEntry.getKana();
		
		document.add(new StringField(LuceneStatic.nameDictionaryEntry_kana, kana, Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.nameDictionaryEntry_kana, kana, generatePrefixes);
		
		if (addSugestionList == true) {
			addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, kana, false);
		}
				
		// romajiList
		String romaji = dictionaryEntry.getRomaji();
		
		document.add(new TextField(LuceneStatic.nameDictionaryEntry_romaji, romaji, Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.nameDictionaryEntry_romaji, romaji, generatePrefixes);
		
		addAlternativeRomaji(document, LuceneStatic.nameDictionaryEntry_virtual_romaji, romaji, generatePrefixes);
				
		if (addSugestionList == true) {
			addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, romaji, false);
		}
		
		// translatesList
		List<String> translates = dictionaryEntry.getTranslates();
		
		for (String currentTranslate : translates) {
			
			document.add(new TextField(LuceneStatic.nameDictionaryEntry_translatesList, currentTranslate, Field.Store.YES));
			
			addPrefixes(document, LuceneStatic.nameDictionaryEntry_translatesList, currentTranslate, generatePrefixes);
			
			if (addSugestionList == true) {
								
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, currentTranslate, true);
			}
			
			String currentTranslateWithoutPolishChars = Utils.removePolishChars(currentTranslate);
				
			document.add(new TextField(LuceneStatic.nameDictionaryEntry_translatesListWithoutPolishChars, currentTranslateWithoutPolishChars, Field.Store.NO));
			
			addPrefixes(document, LuceneStatic.nameDictionaryEntry_translatesListWithoutPolishChars, currentTranslateWithoutPolishChars, generatePrefixes);
		}
		
		// info
		String info = emptyIfNull(dictionaryEntry.getInfo());
		
		document.add(new TextField(LuceneStatic.nameDictionaryEntry_info, info, Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.nameDictionaryEntry_info, info, generatePrefixes);
			
		String infoWithoutPolishChars = Utils.removePolishChars(info);
			
		document.add(new TextField(LuceneStatic.nameDictionaryEntry_infoWithoutPolishChars, infoWithoutPolishChars, Field.Store.NO));
		
		addPrefixes(document, LuceneStatic.nameDictionaryEntry_infoWithoutPolishChars, infoWithoutPolishChars, generatePrefixes);
				
		indexWriter.addDocument(document);
	}
	
	private static void addAvailableRadicalList(IndexWriter indexWriter, Set<String> allAvailableRadicalSet) throws IOException {
		
		List<String> allAvailableRadicalList = new ArrayList<String>(allAvailableRadicalSet);
				
		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.allAvailableKanjiRadicals_objectType, Field.Store.YES));

		for (String radical : allAvailableRadicalList) {
			document.add(new StringField(LuceneStatic.allAvailableKanjiRadicals_radicalsList, radical, Field.Store.YES));
		}		
		
		indexWriter.addDocument(document);
	}
	
	private static void generateSpellChecker(File dbDir, LuceneAnalyzer analyzer, boolean addSugestionList) throws IOException {
		
		if (addSugestionList == true) {
			
			Directory mainIndex = FSDirectory.open(dbDir);			
			IndexReader mainIndexReader = DirectoryReader.open(mainIndex);
			
			//
			
			List<LuceneDatabaseSuggesterAndSpellCheckerSource> luceneDatabaseSuggesterAndSpellCheckerSourceList = new ArrayList<LuceneDatabaseSuggesterAndSpellCheckerSource>();
			
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.DICTIONARY_ENTRY_WEB);
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.DICTIONARY_ENTRY_ANDROID);
			
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.KANJI_ENTRY_WEB);
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.KANJI_ENTRY_ANDROID);

			for (LuceneDatabaseSuggesterAndSpellCheckerSource luceneDatabaseSuggesterAndSpellCheckerSource : luceneDatabaseSuggesterAndSpellCheckerSourceList) {
				
				// tworzenie podindeksu lucene
				File subDbOutDirFile = new File(dbDir, "subindex_" + luceneDatabaseSuggesterAndSpellCheckerSource.getSpellCheckerListFieldName());
				
				if (subDbOutDirFile.isDirectory() == true) {
					
					File[] dbOutDirFileListFiles = subDbOutDirFile.listFiles();
					
					for (File file : dbOutDirFileListFiles) {
						file.delete();
					}
				}
				
				// tworzenie indeksu lucene
				Directory subIndex = FSDirectory.open(subDbOutDirFile);
				
				// wypelnianie podindeksu
				initializeSpellChecker(subIndex, mainIndexReader, analyzer, luceneDatabaseSuggesterAndSpellCheckerSource);
				
				subIndex.close();
			}

			//
			
			mainIndexReader.close();
			mainIndex.close();
		}
	}
	
	private static void initializeSpellChecker(Directory index, IndexReader reader, LuceneAnalyzer analyzer, LuceneDatabaseSuggesterAndSpellCheckerSource source) throws IOException {
		
		System.out.println("Spell Checker: " + source);
		
		// UWAGA: Podobna metoda jest w klasie LuceneDatabase.initializeSpellChecker
		
		LuceneDictionary luceneDictionary = new LuceneDictionary(reader, source.getSpellCheckerListFieldName());
		
		SpellChecker spellChecker = new SpellChecker(index, new JaroWinklerDistance());
				
		IndexWriterConfig indexWriterConfig = new IndexWriterConfig(Version.LUCENE_47, analyzer);
		
		spellChecker.indexDictionary(luceneDictionary, indexWriterConfig, false);
		
		spellChecker.close();
	}	
		
	private static void cacheSuggester(File dbDir, LuceneAnalyzer analyzer, boolean addSugestionList) throws IOException {
				
		if (addSugestionList == true) {
			
			Directory mainIndex = FSDirectory.open(dbDir);
			IndexReader mainIndexReader = DirectoryReader.open(mainIndex);
			
			//
			
			List<LuceneDatabaseSuggesterAndSpellCheckerSource> luceneDatabaseSuggesterAndSpellCheckerSourceList = new ArrayList<LuceneDatabaseSuggesterAndSpellCheckerSource>();
			
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.DICTIONARY_ENTRY_WEB);
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.DICTIONARY_ENTRY_ANDROID);
			
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.KANJI_ENTRY_WEB);
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.KANJI_ENTRY_ANDROID);

			for (LuceneDatabaseSuggesterAndSpellCheckerSource luceneDatabaseSuggesterAndSpellCheckerSource : luceneDatabaseSuggesterAndSpellCheckerSourceList) {
				
				// cache'owanie podpowiadacza
				System.out.println("Suggester cache: " + luceneDatabaseSuggesterAndSpellCheckerSource);
				
				// UWAGA: Podobna metoda jest w klasie LuceneDatabase.initializeSuggester
				LuceneDictionary luceneDictionary = new LuceneDictionary(mainIndexReader, luceneDatabaseSuggesterAndSpellCheckerSource.getSuggestionListFieldName());
				
				AnalyzingSuggester lookup = new AnalyzingSuggester(analyzer);
				
				lookup.build(luceneDictionary);
				
				// zapis do pliku
				lookup.store(new FileOutputStream(new File(dbDir, luceneDatabaseSuggesterAndSpellCheckerSource.getSuggesterCacheFileName())));
			}

			//
			
			mainIndexReader.close();
			mainIndex.close();
		}
	}
	
	private static JMdict readWord2Xml(String word2XmlFilePath) throws JAXBException {
		
		JAXBContext jaxbContext = JAXBContext.newInstance(JMdict.class);              

		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		
		JMdict jmdict = (JMdict) jaxbUnmarshaller.unmarshal(new File(word2XmlFilePath));

		return jmdict;
	}
	
	private static void addWord2Xml(IndexWriter indexWriter, JMdict jmdict) throws IOException {
				
		Gson gson = new Gson();
		
		//

		// pobranie listy wpisow
		List<Entry> entryList = jmdict.getEntryList();

		for (Entry entry : entryList) {
			
			System.out.println("Add word 2 entry: " + entry.getEntryId());
															
			// dodanie do lucynki
			Document document = new Document();
			
			// object type
			document.add(new StringField(LuceneStatic.objectType, LuceneStatic.dictionaryEntry2_objectType, Field.Store.YES));

			// id
			document.add(new IntField(LuceneStatic.dictionaryEntry2_id, entry.getEntryId(), Field.Store.YES));
			
			// xml
			document.add(new StringField(LuceneStatic.dictionaryEntry2_entry, gson.toJson(entry), Field.Store.YES));
			
			//
			
			indexWriter.addDocument(document);
		}
	}
		
	private static String emptyIfNull(String text) {
		if (text == null) {
			return "";
		}

		return text;
	}
	
	private static void addSuggestion(Document document, String fieldName, String fieldValue, boolean isPolishTranslate) { 
		
		if (isPolishTranslate == false) {
			
			document.add(new StringField(fieldName, fieldValue, Field.Store.YES));
			
		} else {
			
			document.add(new StringField(fieldName, fieldValue, Field.Store.YES));
			
			/*
			String fieldValueWithoutPolishChars = Utils.removePolishChars(fieldValue);
			
			if (fieldValue.equals(fieldValueWithoutPolishChars) == false) {
				document.add(new StringField(fieldName, fieldValueWithoutPolishChars + " ___ " / * LuceneDatabaseSuggesterAndSpellCheckerSource.SUGGESTER_SEPARATOR * / + fieldValue, Field.Store.YES));
			}
			*/
		}
	}
	
	private static void addSpellChecker(Document document, String fieldName, String fieldValue) { 		
		document.add(new StringField(fieldName, fieldValue, Field.Store.YES));
	}
	
	private static class Counter {
		
		private int value;
		
		public Counter(int value) {
			this.value = value;
		}
		
		public void increment() {
			value++;
		}

		public int getValue() {
			return value;
		}
	}
}
