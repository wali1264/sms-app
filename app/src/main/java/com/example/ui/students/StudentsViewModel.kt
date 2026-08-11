package com.example.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    val whatsappPhoneInput: String = "",
    val codeInput: String = "",
    val errorMessage: String? = null,
    val toastMessage: String? = null
)

class StudentsViewModel(private val repository: AppRepository) : ViewModel() {

    init {
        viewModelScope.launch {
            try {
                repository.syncWithCloudIfAvailable()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val searchQuery = MutableStateFlow("")
    private val isFormDialogOpen = MutableStateFlow(false)
    private val editingStudent = MutableStateFlow<Student?>(null)
    private val studentToDelete = MutableStateFlow<Student?>(null)

    private val nameInput = MutableStateFlow("")
    private val fatherNameInput = MutableStateFlow("")
    private val smsPhoneInput = MutableStateFlow("")
    private val whatsappPhoneInput = MutableStateFlow("")
    private val codeInput = MutableStateFlow("")

    private val errorMessage = MutableStateFlow<String?>(null)
    private val toastMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<StudentsUiState> = combine(
        searchQuery.flatMapLatest { query -> repository.searchStudents(query) },
        searchQuery,
        isFormDialogOpen,
        editingStudent,
        studentToDelete,
        nameInput,
        fatherNameInput,
        smsPhoneInput,
        whatsappPhoneInput,
        codeInput,
        errorMessage,
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
        val whatsapp = flows[8] as String
        val code = flows[9] as String
        val error = flows[10] as String?
        val toast = flows[11] as String?

        StudentsUiState(
            students = students,
            searchQuery = query,
            isFormDialogOpen = dialog,
            editingStudent = editing,
            studentToDelete = deleting,
            nameInput = name,
            fatherNameInput = father,
            smsPhoneInput = sms,
            whatsappPhoneInput = whatsapp,
            codeInput = code,
            errorMessage = error,
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
        whatsappPhoneInput.value = ""
        codeInput.value = ""
        errorMessage.value = null
        isFormDialogOpen.value = true
    }

    fun openEditStudentDialog(student: Student) {
        editingStudent.value = student
        nameInput.value = student.name
        fatherNameInput.value = student.fatherName
        smsPhoneInput.value = student.smsPhone
        whatsappPhoneInput.value = student.whatsappPhone
        codeInput.value = student.studentCode
        errorMessage.value = null
        isFormDialogOpen.value = true
    }

    fun dismissFormDialog() {
        isFormDialogOpen.value = false
        editingStudent.value = null
        errorMessage.value = null
    }

    fun onNameChanged(value: String) { nameInput.value = value }
    fun onFatherNameChanged(value: String) { fatherNameInput.value = value }
    fun onSmsPhoneChanged(value: String) { smsPhoneInput.value = value }
    fun onWhatsappPhoneChanged(value: String) { whatsappPhoneInput.value = value }
    fun onCodeChanged(value: String) { codeInput.value = value }

    fun saveStudent() {
        val name = nameInput.value.trim()
        val father = fatherNameInput.value.trim()
        val sms = smsPhoneInput.value.trim()
        val whatsapp = whatsappPhoneInput.value.trim()
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
                if (currentEditing == null) {
                    repository.insertStudent(
                        Student(
                            name = name,
                            fatherName = father,
                            smsPhone = sms,
                            whatsappPhone = whatsapp,
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
                            whatsappPhone = whatsapp,
                            studentCode = code
                        )
                    )
                    toastMessage.value = "اطلاعات شاگرد بروزرسانی شد."
                }
                isFormDialogOpen.value = false
                editingStudent.value = null
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
