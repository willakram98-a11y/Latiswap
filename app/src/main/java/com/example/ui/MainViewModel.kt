package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.SynonymPair
import com.example.data.repository.SynonymRepository
import com.example.domain.csv.CsvParser
import com.example.domain.processor.ProcessResult
import com.example.domain.processor.TextProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiNotification(
    val message: String,
    val isError: Boolean = false
)

data class MainUiState(
    val inputText: String = DEFAULT_SAMPLE_TEXT,
    val outputText: String = "",
    val isProcessing: Boolean = false,
    val isLoadingCsv: Boolean = false,
    val lastProcessResult: ProcessResult? = null,
    val notification: UiNotification? = null,
    val showDictionarySheet: Boolean = false,
    val searchQuery: String = ""
)

const val DEFAULT_SAMPLE_TEXT = "Essentially, we can commence the project today to utilize our resources. If this becomes too difficult, we will terminate the operation immediately.\n\nحضر المعلم إلى الفصل مسروراً وكان اليوم جميل جداً."

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SynonymRepository = SynonymRepository(
        AppDatabase.getDatabase(application).synonymDao()
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val dictionaryCount: StateFlow<Int> = repository.synonymCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val allSynonyms: StateFlow<List<SynonymPair>> = repository.allSynonymPairs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Auto-seed sample dictionary on very first launch if DB is empty so the user can test instantly
        viewModelScope.launch {
            val existing = repository.getAllPairs()
            if (existing.isEmpty()) {
                loadSampleDictionary(silent = true)
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun clearInputText() {
        _uiState.update { it.copy(inputText = "") }
    }

    fun pasteInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
        notify("Pasted from clipboard / تم اللصق من الحافظة")
    }

    fun loadSampleText() {
        _uiState.update { it.copy(inputText = DEFAULT_SAMPLE_TEXT) }
        notify("Loaded sample text / تم تحميل نص تجريبي")
    }

    fun clearOutputText() {
        _uiState.update { it.copy(outputText = "", lastProcessResult = null) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setDictionarySheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showDictionarySheet = visible, searchQuery = "") }
    }

    fun dismissNotification() {
        _uiState.update { it.copy(notification = null) }
    }

    private fun notify(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(notification = UiNotification(message, isError)) }
    }

    fun processText() {
        val input = _uiState.value.inputText
        if (input.isBlank()) {
            notify("Please enter or paste some text first / يرجى إدخال نص أولاً", isError = true)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            try {
                val dictionary = repository.getAllPairs()

                if (dictionary.isEmpty()) {
                    _uiState.update { it.copy(isProcessing = false) }
                    notify("No active dictionary! Upload a CSV or load sample pairs / لا يوجد قاموس نشط! يرجى تحميل ملف CSV", isError = true)
                    return@launch
                }

                val result = withContext(Dispatchers.Default) {
                    TextProcessor.process(input, dictionary)
                }

                _uiState.update {
                    it.copy(
                        outputText = result.outputText,
                        lastProcessResult = result,
                        isProcessing = false
                    )
                }

                if (result.totalReplacements > 0) {
                    notify("Replaced ${result.totalReplacements} words / تم استبدال ${result.totalReplacements} كلمات")
                } else {
                    notify("No matching words found in dictionary / لم يتم العثور على كلمات مطابقة في القاموس")
                }
            } catch (e: Throwable) {
                android.util.Log.e("MainViewModel", "Error while processing text", e)
                _uiState.update { it.copy(isProcessing = false) }
                notify("Failed to process text: ${e.localizedMessage ?: "Unknown error"}", isError = true)
            }
        }
    }

    fun importCsvUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCsv = true) }
            try {
                val contentResolver = getApplication<Application>().contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    notify("Failed to open file / تعذر فتح الملف", isError = true)
                    _uiState.update { it.copy(isLoadingCsv = false) }
                    return@launch
                }

                val parseResult = withContext(Dispatchers.IO) {
                    inputStream.use { CsvParser.parse(it) }
                }

                if (parseResult.errorMessage != null || parseResult.pairs.isEmpty()) {
                    notify(parseResult.errorMessage ?: "No valid pairs found in CSV / لم يتم العثور على أزواج صالحة", isError = true)
                } else {
                    repository.saveSynonymPairs(parseResult.pairs)
                    val headerNote = if (parseResult.detectedHeader) " (Header skipped)" else ""
                    notify("Loaded ${parseResult.pairs.size} pairs from CSV$headerNote / تم تحميل ${parseResult.pairs.size} زوج بنجاح")
                }
            } catch (e: Exception) {
                notify("Error reading CSV: ${e.localizedMessage} / خطأ في قراءة الملف", isError = true)
            } finally {
                _uiState.update { it.copy(isLoadingCsv = false) }
            }
        }
    }

    fun clearDictionary() {
        viewModelScope.launch {
            repository.clearAll()
            notify("Database cleared / تم مسح قاعدة البيانات بنجاح")
        }
    }

    fun loadSampleDictionary(silent: Boolean = false) {
        viewModelScope.launch {
            val samplePairs = listOf(
                // English pairs
                SynonymPair("essentially", "basically"),
                SynonymPair("commence", "start"),
                SynonymPair("terminate", "end"),
                SynonymPair("utilize", "use"),
                SynonymPair("purchase", "buy"),
                SynonymPair("difficult", "hard"),
                SynonymPair("enormous", "huge"),
                SynonymPair("immaculate", "spotless"),
                SynonymPair("rapidly", "quickly"),
                SynonymPair("numerous", "many"),
                SynonymPair("comprehend", "understand"),
                SynonymPair("elucidate", "clarify"),
                SynonymPair("substantiate", "prove"),
                SynonymPair("furthermore", "moreover"),
                SynonymPair("subsequently", "afterward"),
                SynonymPair("magnificent", "splendid"),
                SynonymPair("feasible", "possible"),
                SynonymPair("fabricate", "construct"),
                SynonymPair("facilitate", "assist"),
                SynonymPair("in order to", "to"),
                // Arabic pairs
                SynonymPair("المعلم", "الأستاذ"),
                SynonymPair("مسروراً", "فرحاً"),
                SynonymPair("مسرور", "فرحان"),
                SynonymPair("جميل", "رائع"),
                SynonymPair("سريع", "خاطف"),
                SynonymPair("هائل", "ضخم"),
                SynonymPair("طالب", "تلميذ"),
                SynonymPair("سيارة", "مركبة"),
                SynonymPair("منزل", "بيت")
            )
            repository.saveSynonymPairs(samplePairs)
            if (!silent) {
                notify("Loaded ${samplePairs.size} sample pairs / تم تحميل ${samplePairs.size} زوج تجريبي")
            }
        }
    }
}
