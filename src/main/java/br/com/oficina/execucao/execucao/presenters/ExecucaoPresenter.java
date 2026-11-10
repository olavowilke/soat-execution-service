package br.com.oficina.execucao.execucao.presenters;

import br.com.oficina.execucao.execucao.entities.Execucao;

import java.util.List;

public final class ExecucaoPresenter {

    private ExecucaoPresenter() {
    }

    public static ExecucaoResponse detalhe(Execucao e) {
        return new ExecucaoResponse(e.getId(), e.getOsId(), e.getSagaId(), e.getEtapa(),
                e.getServicos().stream()
                        .map(s -> new ExecucaoResponse.Servico(s.descricao(), s.quantidade())).toList(),
                e.getObservacoes(), e.getMotivoRejeicao(), e.getCriadaEm(), e.getAtualizadaEm(),
                e.getHistorico().stream()
                        .map(h -> new ExecucaoResponse.Historico(h.etapa(), h.em())).toList());
    }

    public static ExecucaoResumoResponse resumo(Execucao e) {
        return new ExecucaoResumoResponse(e.getOsId(), e.getEtapa(), e.getCriadaEm());
    }

    public static List<ExecucaoResumoResponse> resumos(List<Execucao> execucoes) {
        return execucoes.stream().map(ExecucaoPresenter::resumo).toList();
    }
}
