package com.victor.chinabedrocktranslator

object TranslationDictionary {
    /**
     * Fallback manual para textos exclusivos da edição chinesa / NetEase.
     * A v0.2 tenta primeiro reaproveitar pt_BR.lang pela mesma chave.
     */
    val ptBr: Map<String, String> = linkedMapOf(
        // Tela de carregamento / teste visual
        "正在下载资源，请勿关闭游戏" to "Baixando recursos, não feche o jogo",
        "剩余时间预计" to "Tempo restante estimado",
        "本次下载" to "Download atual",
        "版本号" to "Versão",
        "引擎" to "Motor",
        "声音" to "Som",
        "速度" to "Velocidade",
        "小时" to "horas",

        // Interface geral
        "开始游戏" to "Jogar",
        "设置" to "Configurações",
        "资源中心" to "Central de Recursos",
        "多人游戏" to "Multijogador",
        "退出登录" to "Sair da conta",
        "登录" to "Entrar",
        "注册" to "Criar conta",
        "下载" to "Baixar",
        "更新" to "Atualizar",
        "确定" to "Confirmar",
        "取消" to "Cancelar",
        "返回" to "Voltar",
        "保存" to "Salvar",
        "删除" to "Excluir",
        "创建" to "Criar",
        "世界" to "Mundo",
        "服务器" to "Servidor",
        "商店" to "Loja",
        "好友" to "Amigos",
        "皮肤" to "Skin",
        "角色" to "Personagem",
        "成就" to "Conquistas",
        "语言" to "Idioma",
        "音乐" to "Música",
        "视频" to "Vídeo",
        "网络" to "Rede",
        "账号" to "Conta",
        "用户" to "Usuário",
        "密码" to "Senha",
        "加载中" to "Carregando",
        "请稍候" to "Espere um momento",
        "错误" to "Erro",
        "成功" to "Sucesso",
        "失败" to "Falha",
        "重试" to "Tentar novamente",
        "继续" to "Continuar",
        "新建" to "Novo",
        "编辑" to "Editar",
        "搜索" to "Pesquisar",
        "分享" to "Compartilhar",
        "安装" to "Instalar",
        "游戏" to "Jogo",
        "退出" to "Sair",
        "连接" to "Conectar",
        "断开连接" to "Desconectar",
        "确认" to "Confirmar",
        "正在加载" to "Carregando",
        "正在连接" to "Conectando",
        "正在下载" to "Baixando",
        "加载资源" to "Carregando recursos",
        "下载资源" to "Baixando recursos"
    )
}
