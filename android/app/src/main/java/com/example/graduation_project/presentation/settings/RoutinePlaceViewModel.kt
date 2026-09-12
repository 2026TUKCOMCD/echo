package com.example.graduation_project.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.model.RoutinePlaceResponse
import com.example.graduation_project.data.repository.RoutinePlaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutinePlaceUiState(
    val isLoading: Boolean = true,
    val consented: Boolean = false,
    val confirmedPlaces: List<RoutinePlaceResponse> = emptyList(),
    val candidates: List<RoutinePlaceResponse> = emptyList(),
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val savedMessage: String? = null
)

/**
 * 루틴 방문 장소(회사/병원 등 반복 방문 장소) 동의·후보 확인·확정 관리.
 *
 * 동의 여부는 항상 서버가 최종 판단하므로(ContextService가 서버에서 직접 확인), 이 뷰모델은
 * UX 편의를 위한 상태만 들고 있고 실제 게이팅은 서버에 위임한다.
 */
class RoutinePlaceViewModel(
    private val repository: RoutinePlaceRepository = RoutinePlaceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutinePlaceUiState())
    val uiState: StateFlow<RoutinePlaceUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val consentResult = repository.getConsent()) {
                is ApiResult.Success -> {
                    if (consentResult.data.consented) {
                        val confirmed = (repository.getConfirmedPlaces() as? ApiResult.Success)?.data.orEmpty()
                        val candidates = (repository.getCandidates() as? ApiResult.Success)?.data.orEmpty()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                consented = true,
                                confirmedPlaces = confirmed,
                                candidates = candidates
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, consented = false, confirmedPlaces = emptyList(), candidates = emptyList())
                        }
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "정보를 불러오지 못했습니다. 다시 시도해주세요.")
                }
            }
        }
    }

    /** 기능 동의 - 이때부터 서버가 방문 이력을 기록하고 패턴 감지를 시작한다 */
    fun grantConsent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            when (repository.setConsent(true)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(isProcessing = false, consented = true, savedMessage = "반복 방문 장소 감지를 시작했어요")
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isProcessing = false, errorMessage = "처리에 실패했습니다. 다시 시도해주세요.")
                }
            }
        }
    }

    /** 동의 철회 - 서버가 저장된 방문 이력·루틴 장소를 즉시 전량 삭제한다 */
    fun revokeConsent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            when (repository.setConsent(false)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isProcessing = false,
                        consented = false,
                        confirmedPlaces = emptyList(),
                        candidates = emptyList(),
                        savedMessage = "감지를 중지하고 저장된 정보를 삭제했어요"
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isProcessing = false, errorMessage = "처리에 실패했습니다. 다시 시도해주세요.")
                }
            }
        }
    }

    /** 후보를 사용자가 라벨과 함께 확정 */
    fun confirmCandidate(id: Long, category: String) {
        if (category.isBlank()) {
            _uiState.update { it.copy(errorMessage = "어떤 곳인지 입력해주세요.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            when (val result = repository.confirm(id, category.trim())) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isProcessing = false,
                        candidates = it.candidates.filterNot { c -> c.id == id },
                        confirmedPlaces = it.confirmedPlaces + result.data,
                        savedMessage = "\"${result.data.category}\"(으)로 등록했어요"
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isProcessing = false, errorMessage = "등록에 실패했습니다. 다시 시도해주세요.")
                }
            }
        }
    }

    /** 후보를 거절 - 같은 장소는 이후 다시 제안되지 않는다 */
    fun dismissCandidate(id: Long) {
        viewModelScope.launch {
            when (repository.delete(id)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(candidates = it.candidates.filterNot { c -> c.id == id })
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(errorMessage = "처리에 실패했습니다. 다시 시도해주세요.")
                }
            }
        }
    }

    /** 확정된 장소의 라벨 수정 */
    fun updateCategory(id: Long, category: String) {
        if (category.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            when (val result = repository.updateCategory(id, category.trim())) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isProcessing = false,
                        confirmedPlaces = it.confirmedPlaces.map { p -> if (p.id == id) result.data else p },
                        savedMessage = "수정했어요"
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isProcessing = false, errorMessage = "수정에 실패했습니다. 다시 시도해주세요.")
                }
            }
        }
    }

    /** 확정된 장소 완전 삭제 */
    fun deletePlace(id: Long) {
        viewModelScope.launch {
            when (repository.delete(id)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        confirmedPlaces = it.confirmedPlaces.filterNot { p -> p.id == id },
                        savedMessage = "삭제했어요"
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(errorMessage = "삭제에 실패했습니다. 다시 시도해주세요.")
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }
    fun dismissSavedMessage() = _uiState.update { it.copy(savedMessage = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return RoutinePlaceViewModel() as T
            }
        }
    }
}
