function showAlert() {
    document.getElementById('error-alert').classList.add('show');
}

function hideAlert() {
    document.getElementById('error-alert').classList.remove('show');
}

onload = (event) => {
    showAlert();
    setTimeout(hideAlert, 5000);
}