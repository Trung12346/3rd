(function () {
  function closeDropdown(item) {
    const trigger = item.querySelector(".room-nav-toggle");
    item.classList.remove("is-open");
    if (trigger) {
      trigger.setAttribute("aria-expanded", "false");
    }
  }

  function initRoomDropdown() {
    const dropdownItems = document.querySelectorAll(".nav-item.has-dropdown");

    dropdownItems.forEach(function (item) {
      if (item.dataset.dropdownReady === "true") {
        return;
      }

      const trigger = item.querySelector(".room-nav-toggle");
      if (!trigger) {
        return;
      }

      item.dataset.dropdownReady = "true";
      trigger.setAttribute("aria-haspopup", "true");
      trigger.setAttribute("aria-expanded", "false");

      trigger.addEventListener("click", function (event) {
        event.preventDefault();
        const shouldOpen = !item.classList.contains("is-open");

        dropdownItems.forEach(closeDropdown);

        if (shouldOpen) {
          item.classList.add("is-open");
          trigger.setAttribute("aria-expanded", "true");
        }
      });
    });

    document.addEventListener("click", function (event) {
      dropdownItems.forEach(function (item) {
        if (!item.contains(event.target)) {
          closeDropdown(item);
        }
      });
    });

    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape") {
        dropdownItems.forEach(closeDropdown);
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initRoomDropdown);
  } else {
    initRoomDropdown();
  }
})();
