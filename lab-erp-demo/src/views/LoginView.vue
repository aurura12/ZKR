<template>
  <div class="login-canvas" :class="{ 'theme-dark': currentThemeDark }">
    <!-- Left Panel with Animated Characters -->
    <section class="left-panel">
      <div class="brand-header">
        <div class="brand-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"/>
          </svg>
        </div>
        <span>智能博弈实验室</span>
      </div>

      <div class="characters-container">
        <!-- Purple Character -->
        <div 
          ref="purpleRef"
          class="character purple-character"
          :class="{ 'typing': isTyping || (form.password.length > 0 && !isPasswordVisible) }"
          :style="getPurpleStyle()"
        >
          <div class="eyes" :style="getPurpleEyesStyle()">
            <div class="eye" :class="{ 'blinking': isPurpleBlinking }">
              <div class="pupil" :style="getPurplePupilStyle()"></div>
            </div>
            <div class="eye" :class="{ 'blinking': isPurpleBlinking }">
              <div class="pupil" :style="getPurplePupilStyle()"></div>
            </div>
          </div>
        </div>

        <!-- Black Character -->
        <div 
          ref="blackRef"
          class="character black-character"
          :class="{ 'looking-each': isLookingAtEachOther }"
          :style="getBlackStyle()"
        >
          <div class="eyes" :style="getBlackEyesStyle()">
            <div class="eye" :class="{ 'blinking': isBlackBlinking }">
              <div class="pupil" :style="getBlackPupilStyle()"></div>
            </div>
            <div class="eye" :class="{ 'blinking': isBlackBlinking }">
              <div class="pupil" :style="getBlackPupilStyle()"></div>
            </div>
          </div>
        </div>

        <!-- Orange Character -->
        <div 
          ref="orangeRef"
          class="character orange-character"
          :style="getOrangeStyle()"
        >
          <div class="pupils" :style="getOrangePupilsStyle()">
            <div class="pupil-small" :style="getOrangePupil1Style()"></div>
            <div class="pupil-small" :style="getOrangePupil2Style()"></div>
          </div>
        </div>

        <!-- Yellow Character -->
        <div 
          ref="yellowRef"
          class="character yellow-character"
          :style="getYellowStyle()"
        >
          <div class="pupils" :style="getYellowPupilsStyle()">
            <div class="pupil-small" :style="getYellowPupil1Style()"></div>
            <div class="pupil-small" :style="getYellowPupil2Style()"></div>
          </div>
          <div class="mouth" :style="getYellowMouthStyle()"></div>
        </div>
      </div>

      <!-- Decorative -->
      <div class="bg-grid"></div>
      <div class="blur-orb blur-orb-1"></div>
      <div class="blur-orb blur-orb-2"></div>
    </section>

    <!-- Right Panel -->
    <section class="right-panel">
      <AuthFlipCardShell :flipped="isRegisterMode">
        <template #login>
          <AuthCredentialsForm
            :account="form.account"
            :password="form.password"
            :password-visible="isPasswordVisible"
            :remember-me="rememberMe"
            :loading="loading"
            :login-error="loginError"
            :show-switch-action="false"
            :show-forgot-password="false"
            @update:account="form.account = $event"
            @update:password="form.password = $event"
            @update:remember-me="rememberMe = $event"
            @focus-input="handleInputFocus"
            @blur-input="handleInputBlur"
            @focus-password="handlePasswordFocus"
            @blur-password="handlePasswordBlur"
            @toggle-password="togglePasswordVisibility"
            @submit="handleLogin"
            @go-register="handleGoRegister"
            @go-forgot-password="showPasswordReset = true"
          />
        </template>

        <template #register>
          <AuthRegistrationForm
            :username="registerForm.username"
            :name="registerForm.name"
            :email="registerForm.email"
            :password="registerForm.password"
            :confirm-password="registerForm.confirmPassword"
            :password-visible="isRegisterPasswordVisible"
            :loading="registerLoading"
            @update:username="registerForm.username = $event"
            @update:name="registerForm.name = $event"
            @update:email="registerForm.email = $event"
            @update:password="registerForm.password = $event"
            @update:confirm-password="registerForm.confirmPassword = $event"
            @focus-input="handleInputFocus"
            @blur-input="handleInputBlur"
            @toggle-password="isRegisterPasswordVisible = !isRegisterPasswordVisible"
            @submit="handleRegister"
            @go-login="handleGoLogin"
          />
        </template>
      </AuthFlipCardShell>
    </section>
  </div>

  <PasswordResetModal v-model="showPasswordReset" @success="handlePasswordResetSuccess" />
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AuthCredentialsForm from '@/components/auth/AuthCredentialsForm.vue'
import AuthFlipCardShell from '@/components/auth/AuthFlipCardShell.vue'
import AuthRegistrationForm from '@/components/auth/AuthRegistrationForm.vue'
import PasswordResetModal from '@/components/auth/PasswordResetModal.vue'
import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const userStore = useUserStore()

const form = reactive({
  account: '',
  password: ''
})

const registerForm = reactive({
  username: '',
  name: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const isRegisterMode = ref(false)
const loading = ref(false)
const registerLoading = ref(false)
const loginError = ref('')
const isPasswordVisible = ref(false)
const isRegisterPasswordVisible = ref(false)
const isPasswordFocused = ref(false)
const currentThemeDark = ref(document.documentElement.classList.contains('dark'))
const rememberMe = ref(false)
const isInputFocused = ref(false)
const showPasswordReset = ref(false)

// Character animation refs
const purpleRef = ref(null)
const blackRef = ref(null)
const yellowRef = ref(null)
const orangeRef = ref(null)

const mouse = ref({ x: window.innerWidth / 2, y: window.innerHeight / 2 })

// Animation states
const isPurpleBlinking = ref(false)
const isBlackBlinking = ref(false)
const isTyping = ref(false)
const isLookingAtEachOther = ref(false)
const isPurplePeeking = ref(false)

let purpleBlinkTimer = null
let blackBlinkTimer = null
let themeObserver = null

// Calculate character positions based on mouse
const calculatePosition = (refEl) => {
  if (!refEl.value) return { faceX: 0, faceY: 0, bodySkew: 0 }

  const rect = refEl.value.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 3

  const deltaX = mouse.value.x - centerX
  const deltaY = mouse.value.y - centerY

  const faceX = Math.max(-15, Math.min(15, deltaX / 20))
  const faceY = Math.max(-10, Math.min(10, deltaY / 30))
  const bodySkew = Math.max(-6, Math.min(6, -deltaX / 120))

  return { faceX, faceY, bodySkew }
}

const purplePos = computed(() => calculatePosition(purpleRef))
const blackPos = computed(() => calculatePosition(blackRef))
const yellowPos = computed(() => calculatePosition(yellowRef))
const orangePos = computed(() => calculatePosition(orangeRef))

// Purple character styles
const getPurpleStyle = () => {
  const pos = purplePos.value
  const height = (isTyping.value || (form.password.length > 0 && !isPasswordVisible.value)) ? '440px' : '400px'
  const transform = (form.password.length > 0 && isPasswordVisible.value)
    ? 'skewX(0deg)'
    : (isTyping.value || (form.password.length > 0 && !isPasswordVisible.value))
      ? `skewX(${(pos.bodySkew || 0) - 12}deg) translateX(40px)`
      : `skewX(${pos.bodySkew || 0}deg)`
  
  return {
    height,
    transform,
    transformOrigin: 'bottom center'
  }
}

const getPurpleEyesStyle = () => {
  const pos = purplePos.value
  const left = (form.password.length > 0 && isPasswordVisible.value) ? '20px' : isLookingAtEachOther.value ? '55px' : `${45 + pos.faceX}px`
  const top = (form.password.length > 0 && isPasswordVisible.value) ? '35px' : isLookingAtEachOther.value ? '65px' : `${40 + pos.faceY}px`
  
  return { left, top }
}

const getPurplePupilStyle = () => {
  if (form.password.length > 0 && isPasswordVisible.value) {
    return {
      transform: `translate(${isPurplePeeking.value ? 4 : -4}px, ${isPurplePeeking.value ? 5 : -4}px)`
    }
  }
  if (isLookingAtEachOther.value) {
    return { transform: 'translate(3px, 4px)' }
  }
  return {}
}

// Black character styles
const getBlackStyle = () => {
  const pos = blackPos.value
  let transform = ''
  
  if (form.password.length > 0 && isPasswordVisible.value) {
    transform = 'skewX(0deg)'
  } else if (isLookingAtEachOther.value) {
    transform = `skewX(${(pos.bodySkew || 0) * 1.5 + 10}deg) translateX(20px)`
  } else if (isTyping.value || (form.password.length > 0 && !isPasswordVisible.value)) {
    transform = `skewX(${(pos.bodySkew || 0) * 1.5}deg)`
  } else {
    transform = `skewX(${pos.bodySkew || 0}deg)`
  }
  
  return {
    transform,
    transformOrigin: 'bottom center'
  }
}

const getBlackEyesStyle = () => {
  const pos = blackPos.value
  const left = (form.password.length > 0 && isPasswordVisible.value) ? '10px' : isLookingAtEachOther.value ? '32px' : `${26 + pos.faceX}px`
  const top = (form.password.length > 0 && isPasswordVisible.value) ? '28px' : isLookingAtEachOther.value ? '12px' : `${32 + pos.faceY}px`
  
  return { left, top }
}

const getBlackPupilStyle = () => {
  if (form.password.length > 0 && isPasswordVisible.value) {
    return { transform: 'translate(-4px, -4px)' }
  }
  if (isLookingAtEachOther.value) {
    return { transform: 'translate(0px, -4px)' }
  }
  return {}
}

// Orange character styles
const getOrangeStyle = () => {
  const pos = orangePos.value
  const transform = (form.password.length > 0 && isPasswordVisible.value) 
    ? 'skewX(0deg)' 
    : `skewX(${pos.bodySkew || 0}deg)`
  
  return {
    transform,
    transformOrigin: 'bottom center'
  }
}

const getOrangePupilsStyle = () => {
  const pos = orangePos.value
  const left = (form.password.length > 0 && isPasswordVisible.value) ? '50px' : `${82 + (pos.faceX || 0)}px`
  const top = (form.password.length > 0 && isPasswordVisible.value) ? '85px' : `${90 + (pos.faceY || 0)}px`
  
  return { left, top }
}

const getOrangePupil1Style = () => {
  if (form.password.length > 0 && isPasswordVisible.value) {
    return { transform: 'translate(-5px, -4px)' }
  }
  return {}
}

const getOrangePupil2Style = () => {
  if (form.password.length > 0 && isPasswordVisible.value) {
    return { transform: 'translate(-5px, -4px)' }
  }
  return {}
}

// Yellow character styles
const getYellowStyle = () => {
  const pos = yellowPos.value
  const transform = (form.password.length > 0 && isPasswordVisible.value) 
    ? 'skewX(0deg)' 
    : `skewX(${pos.bodySkew || 0}deg)`
  
  return {
    transform,
    transformOrigin: 'bottom center'
  }
}

const getYellowPupilsStyle = () => {
  const pos = yellowPos.value
  const left = (form.password.length > 0 && isPasswordVisible.value) ? '20px' : `${52 + (pos.faceX || 0)}px`
  const top = (form.password.length > 0 && isPasswordVisible.value) ? '35px' : `${40 + (pos.faceY || 0)}px`
  
  return { left, top }
}

const getYellowPupil1Style = () => {
  if (form.password.length > 0 && isPasswordVisible.value) {
    return { transform: 'translate(-5px, -4px)' }
  }
  return {}
}

const getYellowPupil2Style = () => {
  if (form.password.length > 0 && isPasswordVisible.value) {
    return { transform: 'translate(-5px, -4px)' }
  }
  return {}
}

const getYellowMouthStyle = () => {
  const pos = yellowPos.value
  const left = (form.password.length > 0 && isPasswordVisible.value) ? '10px' : `${40 + (pos.faceX || 0)}px`
  const top = (form.password.length > 0 && isPasswordVisible.value) ? '88px' : `${88 + (pos.faceY || 0)}px`
  
  return { left, top }
}

// Event handlers
const onMouseMove = (e) => {
  mouse.value.x = e.clientX
  mouse.value.y = e.clientY
}

const togglePasswordVisibility = () => {
  isPasswordVisible.value = !isPasswordVisible.value
}

const handlePasswordFocus = () => {
  isPasswordFocused.value = true
  isTyping.value = true
  
  setTimeout(() => {
    isLookingAtEachOther.value = true
    setTimeout(() => {
      isLookingAtEachOther.value = false
    }, 800)
  }, 100)
}

const handlePasswordBlur = () => {
  isPasswordFocused.value = false
  isTyping.value = false
  isLookingAtEachOther.value = false
}

const handleInputFocus = () => {
  isInputFocused.value = true
  isTyping.value = true
  
  setTimeout(() => {
    isLookingAtEachOther.value = true
    setTimeout(() => {
      isLookingAtEachOther.value = false
    }, 800)
  }, 100)
}

const handleInputBlur = () => {
  isInputFocused.value = false
  isTyping.value = false
  isLookingAtEachOther.value = false
}

// Blinking effects
const startBlinking = () => {
  const getRandomInterval = () => Math.random() * 4000 + 3000
  
  const schedulePurpleBlink = () => {
    purpleBlinkTimer = setTimeout(() => {
      isPurpleBlinking.value = true
      setTimeout(() => {
        isPurpleBlinking.value = false
        schedulePurpleBlink()
      }, 150)
    }, getRandomInterval())
  }
  
  const scheduleBlackBlink = () => {
    blackBlinkTimer = setTimeout(() => {
      isBlackBlinking.value = true
      setTimeout(() => {
        isBlackBlinking.value = false
        scheduleBlackBlink()
      }, 150)
    }, getRandomInterval())
  }
  
  schedulePurpleBlink()
  scheduleBlackBlink()
}

const handleLogin = async () => {
  if (!form.account || !form.password) {
    loginError.value = ''
    ElMessage.warning('请填写账号和密码')
    return
  }

  loginError.value = ''
  loading.value = true
  try {
    await userStore.login({ username: form.account, password: form.password })
    window.location.assign('/finance/classic')
    return
  } catch (error) {
    const message =
      error.response?.data?.message || error.response?.data || error.message || '登录失败'
    loginError.value = message
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

const handleGoRegister = () => {
  loginError.value = ''
  isRegisterMode.value = true
}

const handleGoLogin = () => {
  loginError.value = ''
  isRegisterMode.value = false
  isRegisterPasswordVisible.value = false
}

const resetRegisterForm = () => {
  registerForm.username = ''
  registerForm.name = ''
  registerForm.email = ''
  registerForm.password = ''
  registerForm.confirmPassword = ''
  isRegisterPasswordVisible.value = false
}

const handleRegister = async () => {
  if (!registerForm.username) {
    ElMessage.warning('请填写账号')
    return
  }
  if (!registerForm.name) {
    ElMessage.warning('请填写真实姓名')
    return
  }
  if (!registerForm.email) {
    ElMessage.warning('请填写邮箱')
    return
  }
  if (!EMAIL_PATTERN.test(registerForm.email)) {
    ElMessage.warning('请输入正确的邮箱地址')
    return
  }
  if (!registerForm.password) {
    ElMessage.warning('请填写密码')
    return
  }
  if (registerForm.password.length < 6) {
    ElMessage.warning('密码长度不能少于6位')
    return
  }
  if (!registerForm.confirmPassword) {
    ElMessage.warning('请再次确认密码')
    return
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }

  registerLoading.value = true
  try {
    await userStore.register({
      username: registerForm.username,
      password: registerForm.password,
      name: registerForm.name,
      email: registerForm.email
    })
    ElMessage.success('注册成功，请登录')
    isRegisterMode.value = false
    form.account = registerForm.username
    form.password = ''
    resetRegisterForm()
  } catch (error) {
    const message =
      error.response?.data?.message || error.response?.data || error.message || '注册失败'
    ElMessage.error(message)
  } finally {
    registerLoading.value = false
  }
}

const handlePasswordResetSuccess = () => {
  showPasswordReset.value = false
  form.password = ''
  ElMessage.success('密码重置成功，请使用新密码登录')
}

const syncTheme = () => {
  currentThemeDark.value = document.documentElement.classList.contains('dark')
}

onMounted(() => {
  window.addEventListener('mousemove', onMouseMove)
  startBlinking()

  themeObserver = new MutationObserver(() => {
    syncTheme()
  })

  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class']
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onMouseMove)
  if (themeObserver) {
    themeObserver.disconnect()
    themeObserver = null
  }
  if (purpleBlinkTimer) clearTimeout(purpleBlinkTimer)
  if (blackBlinkTimer) clearTimeout(blackBlinkTimer)
})
</script>

<style scoped>
.login-canvas {
  --primary: #6C3FF5;
  --primary-light: #8B5FFF;
  --page-bg: #ffffff;
  --ink: #000000;
  --line: var(--ink);
  --border-thick: 3px;
  min-height: 100vh;
  display: grid;
  grid-template-columns: 50% 50%;
  background: var(--page-bg);
  color: var(--ink);
}

.login-canvas.theme-dark {
  --page-bg: #0a0a0a;
  --ink: #ffffff;
}

.left-panel,
.right-panel {
  background: var(--page-bg);
  color: var(--ink);
}

.left-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
  overflow: hidden;
}

.brand-header {
  position: relative;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 20px;
  font-weight: 600;
  color: white;
}

.brand-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-icon svg {
  width: 20px;
  height: 20px;
  fill: white;
  stroke: white;
}

.characters-container {
  position: relative;
  z-index: 20;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  height: 400px;
  width: 100%;
  max-width: 550px;
  margin: 0 auto;
}

/* Character Base */
.character {
  position: absolute;
  bottom: 0;
  transition: all 0.7s ease-in-out;
}

/* Purple Character */
.purple-character {
  left: 70px;
  width: 180px;
  background: #6C3FF5;
  border-radius: 10px 10px 0 0;
  z-index: 1;
}

.purple-character .eyes {
  position: absolute;
  display: flex;
  gap: 32px;
}

/* Black Character */
.black-character {
  left: 240px;
  width: 120px;
  height: 310px;
  background: #2D2D2D;
  border-radius: 8px 8px 0 0;
  z-index: 2;
}

.black-character .eyes {
  position: absolute;
  display: flex;
  gap: 24px;
}

/* Orange Character */
.orange-character {
  left: 0;
  width: 240px;
  height: 200px;
  background: #FF9B6B;
  border-radius: 120px 120px 0 0;
  z-index: 3;
}

.orange-character .pupils {
  position: absolute;
  display: flex;
  gap: 32px;
}

/* Yellow Character */
.yellow-character {
  left: 310px;
  width: 140px;
  height: 230px;
  background: #E8D754;
  border-radius: 70px 70px 0 0;
  z-index: 4;
}

.yellow-character .pupils {
  position: absolute;
  display: flex;
  gap: 24px;
}

.yellow-character .mouth {
  position: absolute;
  width: 80px;
  height: 4px;
  background: #2D2D2D;
  border-radius: 2px;
}

/* Eye Styles */
.eye {
  width: 18px;
  height: 18px;
  background: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  transition: height 0.15s ease;
}

.black-character .eye {
  width: 16px;
  height: 16px;
}

.eye.blinking {
  height: 2px;
}

.pupil {
  width: 7px;
  height: 7px;
  background: #2D2D2D;
  border-radius: 50%;
  transition: transform 0.1s ease-out;
}

.black-character .pupil {
  width: 6px;
  height: 6px;
}

.pupil-small {
  width: 12px;
  height: 12px;
  background: #2D2D2D;
  border-radius: 50%;
  transition: transform 0.2s ease-out;
}

/* Decorative */
.bg-grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle, rgba(255,255,255,0.1) 1px, transparent 1px);
  background-size: 20px 20px;
}

.blur-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
}

.blur-orb-1 {
  width: 256px;
  height: 256px;
  background: rgba(255, 255, 255, 0.1);
  top: 25%;
  right: 25%;
}

.blur-orb-2 {
  width: 384px;
  height: 384px;
  background: rgba(255, 255, 255, 0.05);
  bottom: 25%;
  left: 25%;
}

/* Right Panel */
.right-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 32px 40px;
  background: var(--page-bg);
  overflow: hidden;
}

@media (max-width: 1024px) {
  .login-canvas {
    grid-template-columns: 1fr;
  }

  .left-panel {
    min-height: 50vh;
    padding: 24px;
  }

  .characters-container {
    height: 280px;
    transform: scale(0.7);
  }

  .right-panel {
    min-height: auto;
    padding: 24px;
  }
}
</style>
