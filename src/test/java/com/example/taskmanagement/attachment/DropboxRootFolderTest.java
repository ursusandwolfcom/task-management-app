package com.example.taskmanagement.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.taskmanagement.attachment.application.DropboxRootFolder;
import org.junit.jupiter.api.Test;

class DropboxRootFolderTest {
    @Test
    void appModeUsesAppFolderDefault() {
        assertThat(DropboxRootFolder.resolve("app", null, null)).isEqualTo("/projectflow");
        assertThat(DropboxRootFolder.resolve("app", "shared", "")).isEqualTo("/shared");
    }

    @Test
    void teamModeRequiresTeamFolder() {
        assertThat(DropboxRootFolder.resolve("team", "/projectflow", "/ProjectFlow Team"))
                .isEqualTo("/ProjectFlow Team");
        assertThatThrownBy(() -> DropboxRootFolder.resolve("team", "/projectflow", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("team-folder");
    }

    @Test
    void userFolderAppendsUid() {
        assertThat(DropboxRootFolder.userFolder("/ProjectFlow Team", 7L))
                .isEqualTo("/ProjectFlow Team/u7");
    }
}
