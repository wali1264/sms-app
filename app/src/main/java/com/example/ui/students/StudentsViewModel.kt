package com.example.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.SchoolClass
import com.example.data.entity.Student
import com.example.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudentsUiState(
    val students: List<Student> = emptyList(),
    val searchQuery: String = "",
    val isFormDialogOpen: Boolean = false,
    val editingStudent: Student? = null,
    val studentToDelete: Student? = null,
    val nameInput: String = "",
    val fatherNameInput: String = "",
    val smsPhoneInput: String = "",
    val gradeInput: String = "",
    val codeInput: String = "",
    val isCodeDuplicate: Boolean = false,
    val schoolClasses: List<SchoolClass> = emptyList(),
    val errorMessage: String? = null,
    val isSyncing: Boolean = false,
    val toastMessage: String? = null
)

class StudentsViewModel(private val repository: AppRepository) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val isFormDialogOpen = MutableStateFlow(false)
    private val editingStudent = MutableStateFlow<Student?>(null)
    private val studentToDelete = MutableStateFlow<Student?>(null)

    private val nameInput = MutableStateFlow("")
    private val fatherNameInput = MutableStateFlow("")
    private val smsPhoneInput = MutableStateFlow("")
    private val gradeInput = MutableStateFlow("")
    private val codeInput = MutableStateFlow("")
    private val isCodeDuplicate = MutableStateFlow(false)

    private val errorMessage = MutableStateFlow<String?>(null)
    private val isSyncing = MutableStateFlow(false)
    private val toastMessage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            while (true) {
                try {
                    isSyncing.value = true
                    repository.syncWithCloudIfAvailable()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isSyncing.value = false
                }
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    fun manualRefresh() {
        viewModelScope.launch {
            try {
                isSyncing.value = true
                repository.syncWithCloudIfAvailable()
                toastMessage.value = "اطلاعات بروزرسانی شد."
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSyncing.value = false
            }
        }
    }

    val uiState: StateFlow<StudentsUiState> = combine(
        searchQuery.flatMapLatest { query -> repository.searchStudents(query) },
        searchQuery,
        isFormDialogOpen,
        editingStudent,
        studentToDelete,
        nameInput,
        fatherNameInput,
        smsPhoneInput,
        gradeInput,
        codeInput,
        isCodeDuplicate,
        repository.allSchoolClassesFlow,
        errorMessage,
        isSyncing,
        toastMessage
    ) { flows: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val students = flows[0] as List<Student>
        val query = flows[1] as String
        val dialog = flows[2] as Boolean
        val editing = flows[3] as Student?
        val deleting = flows[4] as Student?
        val name = flows[5] as String
        val father = flows[6] as String
        val sms = flows[7] as String
        val grade = flows[8] as String
        val code = flows[9] as String
        val codeDup = flows[10] as Boolean
        @Suppress("UNCHECKED_CAST")
        val classesList = flows[11] as List<SchoolClass>
        val error = flows[12] as String?
        val syncing = flows[13] as Boolean
        val toast = flows[14] as String?

        StudentsUiState(
            students = students,
            searchQuery = query,
            isFormDialogOpen = dialog,
            editingStudent = editing,
            studentToDelete = deleting,
            nameInput = name,
            fatherNameInput = father,
            smsPhoneInput = sms,
            gradeInput = grade,
            codeInput = code,
            isCodeDuplicate = codeDup,
            schoolClasses = classesList,
            errorMessage = error,
            isSyncing = syncing,
            toastMessage = toast
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudentsUiState()
    )

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun openAddStudentDialog() {
        editingStudent.value = null
        nameInput.value = ""
        fatherNameInput.value = ""
        smsPhoneInput.value = ""
        
        viewModelScope.launch {
            val classes = repository.getAllSchoolClasses()
            if (gradeInput.value.isBlank() && classes.isNotEmpty()) {
                gradeInput.value = classes.first().name
            }
            val settings = repository.getSettings()
            if (settings.autoGenerateStudentCode) {
                val nextCode = repository.generateNextStudentCode()
                codeInput.value = nextCode
                isCodeDuplicate.value = false
            } else {
                codeInput.value = ""
                isCodeDuplicate.value = false
            }
            errorMessage.value = null
            isFormDialogOpen.value = true
        }
    }

    fun openEditStudentDialog(student: Student) {
        editingStudent.value = student
        nameInput.value = student.name
        fatherNameInput.value = student.fatherName
        smsPhoneInput.value = student.smsPhone
        gradeInput.value = student.grade
        codeInput.value = student.studentCode
        isCodeDuplicate.value = false
        errorMessage.value = null
        isFormDialogOpen.value = true
    }

    fun dismissFormDialog() {
        isFormDialogOpen.value = false
        editingStudent.value = null
        errorMessage.value = null
        isCodeDuplicate.value = false
    }

    fun onNameChanged(value: String) { nameInput.value = value }
    fun onFatherNameChanged(value: String) { fatherNameInput.value = value }
    fun onSmsPhoneChanged(value: String) { smsPhoneInput.value = value }
    fun onGradeChanged(value: String) { gradeInput.value = value }
    
    fun onCodeChanged(value: String) {
        codeInput.value = value
        viewModelScope.launch {
            val currentEditingId = editingStudent.value?.id ?: 0L
            isCodeDuplicate.value = repository.isStudentCodeDuplicate(value, currentEditingId)
        }
    }

    fun saveStudent() {
        val name = nameInput.value.trim()
        val father = fatherNameInput.value.trim()
        val sms = smsPhoneInput.value.trim()
        val grade = gradeInput.value.trim()
        val code = codeInput.value.trim()

        if (name.isBlank()) {
            errorMessage.value = "لطفاً نام شاگرد را وارد نمایید."
            return
        }
        if (father.isBlank()) {
            errorMessage.value = "لطفاً نام پدر را وارد نمایید."
            return
        }
        if (sms.isBlank()) {
            errorMessage.value = "لطفاً شماره تلفن SMS را وارد نمایید."
            return
        }

        viewModelScope.launch {
            try {
                val currentEditing = editingStudent.value
                val currentEditingId = currentEditing?.id ?: 0L
                if (code.isNotBlank() && repository.isStudentCodeDuplicate(code, currentEditingId)) {
                    isCodeDuplicate.value = true
                    errorMessage.value = "کد شاگرد وارد شده تکراری می‌باشد. لطفاً کد دیگری وارد کنید."
                    return@launch
                }

                if (currentEditing == null) {
                    repository.insertStudent(
                        Student(
                            name = name,
                            fatherName = father,
                            smsPhone = sms,
                            grade = grade,
                            studentCode = code
                        )
                    )
                    toastMessage.value = "شاگرد جدید با موفقیت اضافه شد."
                } else {
                    repository.updateStudent(
                        currentEditing.copy(
                            name = name,
                            fatherName = father,
                            smsPhone = sms,
                            grade = grade,
                            studentCode = code
                        )
                    )
                    toastMessage.value = "اطلاعات شاگرد بروزرسانی شد."
                }
                isFormDialogOpen.value = false
                editingStudent.value = null
                isCodeDuplicate.value = false
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "خطایی رخ داد."
                toastMessage.value = e.localizedMessage ?: "خطایی رخ داد."
            }
        }
    }

    fun confirmDelete(student: Student) {
        studentToDelete.value = student
    }

    fun dismissDeleteDialog() {
        studentToDelete.value = null
    }

    fun deleteStudentConfirmed() {
        val student = studentToDelete.value ?: return
        viewModelScope.launch {
            try {
                repository.deleteStudent(student.id)
                studentToDelete.value = null
                toastMessage.value = "شاگرد حذف شد."
            } catch (e: Exception) {
                toastMessage.value = e.localizedMessage ?: "خطایی رخ داد."
            }
        }
    }

    fun clearToast() {
        toastMessage.value = null
    }

    private data class Tuple7<A, B, C, D, E, F, G>(
        val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G
    )

    class Factory(private val repository: AppRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StudentsViewModel(repository) as T
        }
    }
}
